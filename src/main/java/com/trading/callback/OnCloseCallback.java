package com.trading.callback;

@FunctionalInterface
public interface OnCloseCallback {
    void onClose(int code, String reason);
}
