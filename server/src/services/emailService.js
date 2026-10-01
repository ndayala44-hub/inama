import { config } from "../../config/index.js";

export async function sendOtpEmail(toEmail, otp) {
  if (config.isDev) {
    console.log(`
┌─────────────────────────────────────────────────────────┐
│  📧  PASSWORD RESET OTP (dev mode — no email provider)   │
│  To: ${toEmail.padEnd(53)}│
│  Code: ${otp.padEnd(51)}│
│  Expires in 10 minutes.                                  │
└─────────────────────────────────────────────────────────┘
`);
    return { delivered: false, devMode: true };
  }

  throw new Error(
    "No email provider is configured. Set up a real email service (e.g. nodemailer + SMTP, SendGrid, SES) before enabling password reset in production.",
  );
}
