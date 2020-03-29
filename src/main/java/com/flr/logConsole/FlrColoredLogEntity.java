package com.flr.logConsole;

import com.sun.istack.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FlrColoredLogEntity {
    public static class Item {
        public String text;
        public FlrLogConsole.LogType logType = FlrLogConsole.LogType.normal;

        public Item(@NotNull String text, @NotNull FlrLogConsole.LogType logType) {
            this.text = text;
            this.logType = logType;
        }
    }

    public List<Item> items;

    public FlrColoredLogEntity() {
        items = new ArrayList<Item>();
    }

    public FlrColoredLogEntity(@NotNull List<Item> items) {
        this.items = items;
    }

}
