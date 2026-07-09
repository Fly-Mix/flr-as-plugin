package com.flr

/**
 * Flr 异常：当插件收到这个异常时，应终止当前任务程序。
 *
 * 继承 `java.lang.Exception`，故对 Java 调用方仍是受检异常，现有 `throws FlrException` 声明保持有效。
 */
class FlrException(message: String) : Exception(message) {

    companion object {
        /** 环境非法的通用异常实例（Java 端以 `FlrException.ILLEGAL_ENV` 访问） */
        @JvmField
        val ILLEGAL_ENV = FlrException("[*]: found illegal environment, you can get the details from Flr ToolWindow")
    }
}
