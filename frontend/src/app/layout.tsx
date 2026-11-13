import type { Metadata } from "next";
import { Inter, Noto_Sans_Khmer } from "next/font/google";
import { AuthProvider } from "@/components/auth/auth-provider";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

const notoSansKhmer = Noto_Sans_Khmer({
  variable: "--font-noto-khmer",
  subsets: ["khmer"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Smart Certificate Generator",
    template: "%s | Smart Certificate Generator",
  },
  description:
    "Organization-focused certificate design, issuance, management, and public verification platform.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${notoSansKhmer.variable} h-full antialiased`}>
      <body className="min-h-full bg-bg-app text-text-main">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
