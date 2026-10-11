# HDRG ResolumeTools Portal

Public GitHub Pages static catalog. Splash, category filters, project cards, and GitHub Actions artifact page links.

## Security decision
GitHub Pages **cannot** securely verify usernames and PINs or enforce gated downloads. No master PIN has been committed. Admin uses GitHub repository permission settings. User creation with individual PINs requires a separate authentication backend. Do not add static PIN checks to client JS, including obfuscated code.

## Publish
GitHub repository Settings → Pages → Build and deployment: Deploy from a branch → main → /(root). Site URL after successful deploy: https://hdrgcreativepartner-del.github.io/ResolumeTools/

The Audio Bridge card links to Windows workflow runs rather than claiming a prebuilt EXE. Download the successful workflow's Artifacts ZIP.
