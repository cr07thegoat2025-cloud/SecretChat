# ChattingApp

A native Android messaging app built with Kotlin and Jetpack Compose.

## Status

Initial project setup is being added. Backend: Supabase (Auth, Postgres, Realtime, Storage).

## Build

Pushes to `main` trigger the Android GitHub Actions build. The debug APK is uploaded as a workflow artifact.

## Configuration

The Supabase project URL and publishable key are public client configuration. Configure them as GitHub Actions repository variables `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` before building a connected release. Never add a Supabase secret/service-role key to the app.
