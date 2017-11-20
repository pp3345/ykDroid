package net.pp3345.yubidroid.yubikey;

/**
 * Exception thrown when an operation would block waiting for user interaction although it was not
 * expected to block.
 */
public class BlockingOperationException extends YubiKeyException {
}
