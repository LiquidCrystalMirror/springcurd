package org.example.springbootdemo.constant.enums;

import lombok.Getter;

@Getter
public enum ScriptConstant {

    BATCH_DEDUCT("lua/batch_deduct.lua", "deduct"),
    ROLLBACK("lua/rollback.lua", "rollback"),
    QUERY("lua/query.lua", "query");

    private final String path;
    private final String type;

    ScriptConstant(String path, String type) {
        this.path = path;
        this.type = type;
    }
}