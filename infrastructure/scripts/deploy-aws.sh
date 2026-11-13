#!/usr/bin/env bash
set -euo pipefail

# Smart Certificate Generator - AWS Deployment & ECR Publishing CLI
ACTION="${1:-plan}"
REGION="${AWS_REGION:-ap-southeast-2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="${SCRIPT_DIR}/../aws/terraform"
TF_VARS="${TF_DIR}/terraform.tfvars"
ENV_AWS="${SCRIPT_DIR}/../../.env.aws"

if [ -f "${ENV_AWS}" ]; then
  # Export variables from .env.aws safely
  set -a
  # shellcheck source=/dev/null
  . "${ENV_AWS}"
  set +a
fi

if [ ! -f "${TF_VARS}" ]; then
  echo "[INFO] Creating terraform.tfvars from example..."
  cp "${TF_DIR}/terraform.tfvars.example" "${TF_VARS}"
fi

echo "=========================================================="
echo "Smart Certificate Generator - AWS Deployment Assistant"
echo "Action: ${ACTION} | Region: ${REGION}"
echo "=========================================================="

AWS_MOUNT=""
if [ -d "${HOME}/.aws" ]; then
  AWS_MOUNT="-v ${HOME}/.aws:/root/.aws:ro"
fi

IDENTITY_JSON=$(docker run --rm ${AWS_MOUNT} \
  -e AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-}" \
  -e AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-}" \
  -e AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN:-}" \
  -e AWS_DEFAULT_REGION="${REGION}" \
  amazon/aws-cli:latest sts get-caller-identity)

ACCOUNT_ID=$(echo "${IDENTITY_JSON}" | grep -o '"Account": "[^"]*' | cut -d'"' -f4)
echo "[SUCCESS] Authenticated with AWS Account: ${ACCOUNT_ID}"

TF_ENV=(
  "-e" "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-}"
  "-e" "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-}"
  "-e" "AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN:-}"
  "-e" "AWS_DEFAULT_REGION=${REGION}"
)

case "${ACTION}" in
  "init")
    docker run --rm ${AWS_MOUNT} "${TF_ENV[@]}" -v "${TF_DIR}:/workspace" -w /workspace hashicorp/terraform:latest init
    ;;
  "plan")
    docker run --rm ${AWS_MOUNT} "${TF_ENV[@]}" -v "${TF_DIR}:/workspace" -w /workspace hashicorp/terraform:latest plan
    ;;
  "apply")
    docker run -it --rm ${AWS_MOUNT} "${TF_ENV[@]}" -v "${TF_DIR}:/workspace" -w /workspace hashicorp/terraform:latest apply
    ;;
  "destroy")
    docker run -it --rm ${AWS_MOUNT} "${TF_ENV[@]}" -v "${TF_DIR}:/workspace" -w /workspace hashicorp/terraform:latest destroy
    ;;
  "status")
    docker run --rm ${AWS_MOUNT} "${TF_ENV[@]}" -v "${TF_DIR}:/workspace" -w /workspace hashicorp/terraform:latest output
    ;;
  *)
    echo "Unknown action: ${ACTION}. Available: init, plan, apply, destroy, status"
    exit 1
    ;;
esac
