package com.smartcertificate.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcertificate.common.config.StorageProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3StorageServiceTest {

    private S3Client s3Client;
    private StorageProperties properties;
    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        properties = new StorageProperties(
                "s3",
                "./data",
                "secret-key-at-least-32-bytes-long-for-hmac",
                300,
                5242880,
                "smart-cert-prod-bucket",
                "ap-southeast-1",
                "production-tenant",
                null,
                null);
        storageService = new S3StorageService(properties, s3Client);
    }

    @Test
    void store_putsObjectWithAes256EncryptionAndPrefixedKey() {
        byte[] payload = "test-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.store("certs/cert-123.pdf", payload);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo("smart-cert-prod-bucket");
        assertThat(req.key()).isEqualTo("production-tenant/certs/cert-123.pdf");
        assertThat(req.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    }

    @Test
    void store_withKmsKeyId_appliesKmsEncryption() {
        StorageProperties kmsProps = new StorageProperties(
                "s3",
                "./data",
                "secret-key-at-least-32-bytes-long-for-hmac",
                300,
                5242880,
                "smart-cert-prod-bucket",
                "ap-southeast-1",
                "",
                null,
                "arn:aws:kms:ap-southeast-1:123456789012:key/test-kms-key");
        S3StorageService kmsService = new S3StorageService(kmsProps, s3Client);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        kmsService.store("certs/cert-456.pdf", "data".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.key()).isEqualTo("certs/cert-456.pdf");
        assertThat(req.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
        assertThat(req.ssekmsKeyId()).isEqualTo("arn:aws:kms:ap-southeast-1:123456789012:key/test-kms-key");
    }

    @Test
    void load_returnsObjectBytesWhenFound() {
        byte[] expected = "loaded-bytes".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = GetObjectResponse.builder().build();
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(response, expected);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] actual = storageService.load("certs/cert-123.pdf");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void load_throwsAppExceptionNotFoundOnNoSuchKey() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        assertThatThrownBy(() -> storageService.load("missing.pdf"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void deleteIfExists_sendsDeleteRequest() {
        storageService.deleteIfExists("certs/cert-123.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("production-tenant/certs/cert-123.pdf");
    }
}
