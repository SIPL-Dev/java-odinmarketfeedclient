package com.trading.callback;
// package com.trading;

@FunctionalInterface
public interface OnErrorCallback {
    void onError(String error);
}