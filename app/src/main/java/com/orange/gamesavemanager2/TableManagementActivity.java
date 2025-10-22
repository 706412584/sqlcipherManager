package com.orange.gamesavemanager2;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Map;

import game.core.DBCipherManager;
import game.core.DatabaseConfig;
import game.core.TableManager;

/**
 * 表管理演示Activity - 展示表结构查看、字段管理等功能
 */
public class TableManagementActivity extends AppCompatActivity {

    private TextView tvContent;
    private DBCipherManager dbManager;
    private TableManager tableManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_table_management);
            
            tvContent = findViewById(R.id.tv_content);
            if (tvContent == null) {
                // 如果布局没有tv_content，创建简单的文本显示
                tvContent = new TextView(this);
                setContentView(tvContent);
            }
            
            initDatabase();
            showTableInfo();
            
            Toast.makeText(this, "表管理演示功能", Toast.LENGTH_SHORT).show();
            AppLogger.i("TableManagement", "page_open");
        } catch (Exception e) {
            AppLogger.e("TableManagement", "onCreate error: " + e.getMessage());
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initDatabase() {
        try {
            // 从SharedPreferences读取数据库配置
            android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
            String dbName = prefs.getString("db_name", "demo_database");
            String password = prefs.getString("db_password", "demo123");
            
            DatabaseConfig config = new DatabaseConfig.Builder()
                    .setDatabaseName(dbName)
                    .setPassword(password)
                    .setVersion(1)
                    .build();
            
            dbManager = DBCipherManager.getInstance(this, config);
            tableManager = dbManager.getTableManager();
            
            appendLog("数据库管理器初始化成功\n");
            appendLog("当前数据库: " + dbName + "\n\n");
            AppLogger.d("TableManagement", "使用数据库: " + dbName);
        } catch (Exception e) {
            appendLog("数据库管理器初始化失败: " + e.getMessage() + "\n");
            AppLogger.e("TableManagement", "init error: " + e.getMessage());
        }
    }

    private void showTableInfo() {
        new Thread(() -> {
            try {
                if (tableManager == null) {
                    appendLog("错误: 表管理器未初始化\n");
                    return;
                }
                
                // 获取所有表名
                List<String> tables = tableManager.getAllTableNames();
                appendLog("=== 数据库表列表 ===\n");
                if (tables != null && !tables.isEmpty()) {
                    appendLog("共找到 " + tables.size() + " 个表:\n\n");
                    for (String tableName : tables) {
                        appendLog("表名: " + tableName + "\n");
                        
                        // 获取表结构
                        List<Map<String, String>> structure = tableManager.getTableStructure(tableName);
                        if (structure != null && !structure.isEmpty()) {
                            appendLog("  字段数: " + structure.size() + "\n");
                            for (Map<String, String> column : structure) {
                                String name = column.get("name");
                                String type = column.get("type");
                                appendLog("    - " + name + " (" + type + ")\n");
                            }
                        }
                        appendLog("\n");
                    }
                } else {
                    appendLog("数据库中暂无用户表\n");
                    appendLog("\n提示: 请先在'数据库操作演示'中创建表\n");
                }
                
                AppLogger.i("TableManagement", "show_table_info_complete");
                
            } catch (Exception e) {
                appendLog("获取表信息失败: " + e.getMessage() + "\n");
                AppLogger.e("TableManagement", "showTableInfo error: " + e.getMessage());
            }
        }).start();
    }

    private void appendLog(String message) {
        if (tvContent != null) {
            runOnUiThread(() -> tvContent.append(message));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbManager != null) {
            dbManager.closeAllConnections();
        }
    }
}
