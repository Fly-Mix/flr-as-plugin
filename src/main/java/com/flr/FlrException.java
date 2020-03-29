package com.flr;

/*
* Flr异常
* 当插件收到这个异常时，应该终止当前的任务程序
* */
public class FlrException extends Exception {

    public FlrException(String message) {
        super(message);
    }

    public static final FlrException ILLEGAL_ENV = new FlrException("[*]: found illegal environment, you can get the details from Flr ToolWindow");


}
