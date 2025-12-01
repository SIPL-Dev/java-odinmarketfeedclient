package com.trading.callback;
// package com.trading;

@FunctionalInterface
public interface OnMessageCallback {
    void onMessage(String message);
}