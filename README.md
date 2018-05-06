# ykDroid
ykDroid is an Android app that provides an interface for integrating the challenge-response functionality
of YubiKeys into other apps. Both USB and NFC (YubiKey NEO required for NFC) are supported on compatible devices.

## Integration
ykDroid provides an [Intent](https://developer.android.com/reference/android/content/Intent.html) called
`net.pp3345.ykdroid.intent.action.CHALLENGE_RESPONSE`, which accepts an extra `byte[] challenge` and returns an extra
`byte[] response`.
Upon invocation, ykDroid will automatically detect which connection methods (USB and/or NFC) are available and show a dialog
overlay with instructions to the user.

Example code:
```java
private static final int CHALLENGE_RESPONSE_REQUEST_CODE = 12345;

private void startChallengeResponse(final byte[] challenge) {
    final Intent intent = new Intent("net.pp3345.ykdroid.intent.action.CHALLENGE_RESPONSE");
    intent.putExtra("challenge", challenge);

    startActivityForResult(intent, CHALLENGE_RESPONSE_REQUEST_CODE);
}

public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if(requestCode == CHALLENGE_RESPONSE_REQUEST_CODE && resultCode == RESULT_OK) {
        final byte[] response = data.getByteArrayExtra("response");
        // ...
    }
}
```

## Contributing
PRs welcome! I am open for adding more YubiKey functionality, but bugfixes and additional translations are also very appreciated :-)

## Bugs & issues
Please use the GitHub issue tracker for reporting bugs and feature requests.

## Notice
Yubico and YubiKey are registered trademarks of Yubico.
