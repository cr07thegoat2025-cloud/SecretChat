# ChattingApp

Native Android messaging app built with Kotlin, Jetpack Compose and Supabase.

## Backend
Supabase project: `https://sgzbbbrhoskipnvclskl.supabase.co`. Database migration creates profiles, friend requests, conversations, memberships, messages, RLS policies and a private attachment bucket.

## GitHub Actions variables
In Settings → Secrets and variables → Actions → Variables, add `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. Use the Supabase publishable key, never a secret/service-role key.

## Username-only authentication
The app maps usernames to internal addresses at `@chattingapp.invalid` for Supabase email/password auth. No real email or phone number is collected. Disable email confirmation in Supabase Auth settings for this password-only flow.
