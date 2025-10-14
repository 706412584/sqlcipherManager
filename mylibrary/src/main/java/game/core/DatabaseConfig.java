package game.core;

import java.lang.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.nio.charset.StandardCharsets; // 统一编码，避免跨平台问题

/**
 * 数据库配置信息封装类
 * 集中管理数据库名称、密码（支持String/char[]/byte[]）、版本、自动优化等元数据
 */
public class DatabaseConfig {
    
    private String databaseName;
    private char[] password;
    private int version;
    private Map<String, String> tableSchemas = new HashMap<>();
    private boolean autoOptimize = true; //是否开启自动优化，默认开启
    
    private DatabaseConfig(Builder builder) {
        this.databaseName = builder.databaseName;
        this.password = builder.password;
        this.version = builder.version;
        this.tableSchemas = builder.tableSchemas;
        this.autoOptimize = builder.autoOptimize; // 初始化自动优化配置
    }
    
    public static class Builder {
        private String databaseName;
        private char[] password;
        private int version = 1; // 默认版本号
        private Map<String, String> tableSchemas = new HashMap<>();
        private boolean autoOptimize = true; // 【新增】默认开启自动优化
        
        public Builder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }
        
        public Builder setPassword(char[] password) {
            this.clearPassword();
            this.password = (password == null) ? null : Arrays.copyOf(password, password.length);
            return this;
        }
        
        public Builder setPassword(String password) {
            this.clearPassword();
            this.password = (password == null) ? null : password.toCharArray();
            return this;
        }
        
        public Builder setPassword(byte[] password) {
            this.clearPassword();
            this.password = bytesToChars(password);
            return this;
        }
        
        public Builder setVersion(int version) {
            this.version = version;
            return this;
        }
        
        // 设置是否开启自动优化
        public Builder setAutoOptimize(boolean autoOptimize) {
            this.autoOptimize = autoOptimize;
            return this;
        }
        
        public Builder addTableSchema(String tableName, String schema) {
            if (tableName != null && schema != null) {
                tableSchemas.put(tableName, schema);
            }
            return this;
        }
        
        public Builder addTableSchema(Map<String, String> mp) {
            if (mp == null || mp.isEmpty()) {
                return this;
            }
            for (Map.Entry<String, String> entry : mp.entrySet()) {
                String tableName = entry.getKey();
                String schema = entry.getValue();
                tableSchemas.put(tableName, schema);
            }
            return this;
        }
        
        public void clearPassword() {
            if (password != null) {
                Arrays.fill(password, '\0');
                password = null;
            }
        }
        
        public DatabaseConfig build() {
            if (databaseName == null || databaseName.trim().isEmpty()) {
                throw new IllegalArgumentException("Database name cannot be null or empty");
            }
            return new DatabaseConfig(this);
        }
        
        // 工具方法 - byte[] 转 char[]
        private static char[] bytesToChars(byte[] bytes) {
            if (bytes == null) return null;
            try {
                return new String(bytes, StandardCharsets.UTF_8).toCharArray();
            } catch (Exception e) {
                throw new RuntimeException("byte[] 转 char[] 失败（编码：UTF-8）", e);
            }
        }
    }
    
    // ==================== Getters ====================
    public String getDatabaseName() { return databaseName; }
    public int getVersion() { return version; }
    public char[] getPassword() { return (password == null) ? null : Arrays.copyOf(password, password.length); }
    public String getPasswordAsString() { return (password == null) ? null : new String(password); }
    public byte[] getPasswordAsBytes() { return charsToBytes(password); }
    public Map<String, String> getTableSchemas() { return Collections.unmodifiableMap(tableSchemas); }
    public boolean isAutoOptimizeEnabled() { return autoOptimize;}
    
    // ==================== 工具方法 ====================
    private static char[] bytesToChars(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return new String(bytes, StandardCharsets.UTF_8).toCharArray();
        } catch (Exception e) {
            throw new RuntimeException("byte[] 转 char[] 失败（编码：UTF-8）", e);
        }
    }
    
    private static byte[] charsToBytes(char[] chars) {
        if (chars == null) return null;
        try {
            return new String(chars).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("char[] 转 byte[] 失败（编码：UTF-8）", e);
        }
    }
    
    public void clearPassword() {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
            this.password = null;
        }
    }
}
