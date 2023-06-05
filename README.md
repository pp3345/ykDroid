# ykDroid

ykDroid is an Android app that provides an interface for integrating the challenge-response functionality
of YubiKeys into other apps. Both USB and NFC (YubiKey NEO required for NFC) are supported on compatible devices.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=net.pp3345.ykdroid)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/net.pp3345.ykdroid/)

## Integration
ykDroid provides an [Intent](https://developer.android.com/reference/android/content/Intent.html) called
`android.yubikey.intent.action.CHALLENGE_RESPONSE`, which accepts an extra `byte[] challenge` and returns an extra
`byte[] response`. Optionally, an extra `String purpose` may be passed additionally in the intent to identify the purpose
of the challenge. ykDroid will use this identifier to remember and pre-select the slot used for each purpose.

Upon invocation, ykDroid will automatically detect which connection methods (USB and/or NFC) are available and show a dialog
overlay with instructions to the user.

Example code:
```java
private static final int CHALLENGE_RESPONSE_REQUEST_CODE = 12345;

private void startChallengeResponse(final byte[] challenge) {
    final Intent intent = new Intent("android.yubikey.intent.action.CHALLENGE_RESPONSE");
    intent.putExtra("challenge", challenge);
    intent.putExtra("purpose", "some-unique-purpose-identifier"); // optional

    startActivityForResult(intent, CHALLENGE_RESPONSE_REQUEST_CODE);
}

public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if(requestCode == CHALLENGE_RESPONSE_REQUEST_CODE && resultCode == RESULT_OK) {
        final byte[] response = data.getByteArrayExtra("response");
        // ...
    }
}
```

## Apps that integrate ykDroid
* [Keepass2Android](https://play.google.com/store/apps/details?id=keepass2android.keepass2android) - Password manager
  app compatible with KeePass
* [ykpass](https://github.com/noliran/ykpass) - Password app that derives passwords directly from the YubiKey's response value
* [KeePassDX](https://www.keepassdx.com/) - Another password manager app compatible with KeePass

## Contributing
PRs welcome! I am open for adding more YubiKey functionality, but bugfixes and additional translations are also very
appreciated :-)

## Bugs & issues
Please use the GitHub issue tracker for reporting bugs and feature requests.

## Notice
Yubico and YubiKey are registered trademarks of Yubico. Google Play and the Google Play logo are trademarks of Google LLC.
