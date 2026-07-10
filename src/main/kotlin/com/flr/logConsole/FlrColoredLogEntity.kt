package com.flr.logConsole

/**
 * 彩色日志实体：多段 (text, logType) 的组合。字段以 @JvmField 暴露为公有字段，保持 Java 端 `item.text` 访问。
 */
class FlrColoredLogEntity {

    class Item(
        @JvmField var text: String,
        @JvmField var logType: FlrLogConsole.LogType
    )

    @JvmField
    var items: MutableList<Item>

    constructor() {
        items = ArrayList()
    }

    constructor(items: MutableList<Item>) {
        this.items = items
    }
}
