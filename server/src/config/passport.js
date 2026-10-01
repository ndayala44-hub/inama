import passport from "passport";
import { Strategy as GoogleStrategy } from "passport-google-oauth20";
import { config } from "../../config/index.js";
import { User, Notification } from "../models/index.js";

const isGoogleConfigured = config.google.clientId && config.google.clientSecret;

if (isGoogleConfigured) {
  passport.use(
    new GoogleStrategy(
      {
        clientID: config.google.clientId,
        clientSecret: config.google.clientSecret,
        callbackURL: config.google.callbackUrl,
      },
      async (_accessToken, _refreshToken, profile, done) => {
        try {
          const email = profile.emails?.[0]?.value?.toLowerCase();
          if (!email)
            return done(new Error("Google account has no email"), null);

          let user = await User.findOne({
            $or: [{ googleId: profile.id }, { email }],
          });

          if (!user) {
            user = await User.create({
              name: profile.displayName || email.split("@")[0],
              email,
              googleId: profile.id,
              authProvider: "google",
            });
            await Notification.create({
              userId: user._id,
              type: "system",
              title: "Welcome to AgriAI!",
              message:
                "Your account is ready. Start by asking a farming question in the AI Advisory Chat.",
            });
          } else if (!user.googleId) {
            user.googleId = profile.id;
            if (user.authProvider === "local") user.authProvider = "local"; // keep original provider
            await user.save({ validateModifiedOnly: true });
          }

          return done(null, user);
        } catch (err) {
          return done(err, null);
        }
      },
    ),
  );
}

export { isGoogleConfigured };
export default passport;
