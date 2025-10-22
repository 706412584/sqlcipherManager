package com.orange.gamesavemanager2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import game.core.DBCipherManager;
import game.core.DatabaseConfig;
import game.core.SqlUtilManager;
import game.core.TableManager;

/**
 * 数据库操作演示Activity
 * 展示mylibrary核心库的数据库操作功能
 */
public class DatabaseDemoActivity extends AppCompatActivity implements View.OnClickListener {

    // UI组件
    private Toolbar toolbar;
    private TextInputEditText etDatabaseName;
    private TextInputEditText etPassword;
    private TextInputEditText etTableName;
    private TextView tvLog;
    
    // 按钮
    private Button btnCreateDatabase;
    private Button btnCreateTable;
    private Button btnInsertData;
    private Button btnQueryData;
    private Button btnUpdateData;
    private Button btnDeleteData;
    private Button btnExportJson;
    private Button btnImportJson;
    private Button btnClearLog;
    private Button btnCopyLog;
    
    // 数据库管理器
    private DBCipherManager dbManager;
    private SqlUtilManager sqlUtilManager;
    private TableManager tableManager;
    
    // 线程处理
    private Handler mainHandler;
    
    // 日志缓冲区
    private StringBuilder logBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_database_demo);

            // 先初始化线程与日志缓冲，避免后续任意日志写入空指针
            mainHandler = new Handler(Looper.getMainLooper());
            logBuffer = new StringBuilder();

            initViews();
            setupClickListeners();
            initDatabase();
            
            logMessage("数据库演示界面已启动");
            AppLogger.i("DatabaseDemo", "page_open");
        } catch (Exception e) {
            AppLogger.e("DatabaseDemo", "onCreate error: " + e.getMessage());
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 初始化UI组件
     */
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        etDatabaseName = findViewById(R.id.et_database_name);
        etPassword = findViewById(R.id.et_password);
        etTableName = findViewById(R.id.et_table_name);
        tvLog = findViewById(R.id.tv_log);
        
        btnCreateDatabase = findViewById(R.id.btn_create_database);
        btnCreateTable = findViewById(R.id.btn_create_table);
        btnInsertData = findViewById(R.id.btn_insert_data);
        btnQueryData = findViewById(R.id.btn_query_data);
        btnUpdateData = findViewById(R.id.btn_update_data);
        btnDeleteData = findViewById(R.id.btn_delete_data);
        btnExportJson = findViewById(R.id.btn_export_json);
        btnImportJson = findViewById(R.id.btn_import_json);
        btnClearLog = findViewById(R.id.btn_clear_log);
        btnCopyLog = findViewById(R.id.btn_copy_log);
        
        // 设置工具栏
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        btnCreateDatabase.setOnClickListener(this);
        btnCreateTable.setOnClickListener(this);
        btnInsertData.setOnClickListener(this);
        btnQueryData.setOnClickListener(this);
        btnUpdateData.setOnClickListener(this);
        btnDeleteData.setOnClickListener(this);
        btnExportJson.setOnClickListener(this);
        btnImportJson.setOnClickListener(this);
        btnClearLog.setOnClickListener(this);
        btnCopyLog.setOnClickListener(this);
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * 初始化数据库管理器
     */
    private void initDatabase() {
        try {
            // 从SharedPreferences读取上次使用的数据库配置
            android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
            String dbName = prefs.getString("db_name", "demo_database");
            String password = prefs.getString("db_password", "demo123");
            
            // 创建数据库配置
            DatabaseConfig config = new DatabaseConfig.Builder()
                    .setDatabaseName(dbName)
                    .setPassword(password)
                    .setVersion(1)
                    .build();
            
            // 获取数据库管理器实例
            dbManager = DBCipherManager.getInstance(this, config);
            sqlUtilManager = dbManager.getSqlUtilManager();
            tableManager = dbManager.getTableManager();
            
            logMessage("数据库管理器初始化成功");
            AppLogger.d("DatabaseDemo", "使用数据库: " + dbName);
            
        } catch (Exception e) {
            logMessage("数据库管理器初始化失败: " + e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        
        if (id == R.id.btn_create_database) {
            createDatabase();
        } else if (id == R.id.btn_create_table) {
            createTable();
        } else if (id == R.id.btn_insert_data) {
            insertData();
        } else if (id == R.id.btn_query_data) {
            queryData();
        } else if (id == R.id.btn_update_data) {
            updateData();
        } else if (id == R.id.btn_delete_data) {
            deleteData();
        } else if (id == R.id.btn_export_json) {
            exportJson();
        } else if (id == R.id.btn_import_json) {
            importJson();
        } else if (id == R.id.btn_clear_log) {
            clearLog();
        } else if (id == R.id.btn_copy_log) {
            copyLog();
        }
    }

    /**
     * 创建数据库
     */
    private void createDatabase() {
        runInBackground(() -> {
            try {
                String dbName = etDatabaseName.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                
                if (TextUtils.isEmpty(dbName) || TextUtils.isEmpty(password)) {
                    logMessage("数据库名称和密码不能为空");
                    return;
                }
                
                // 创建新的数据库配置
                DatabaseConfig config = new DatabaseConfig.Builder()
                        .setDatabaseName(dbName)
                        .setPassword(password)
                        .setVersion(1)
                        .build();
                
                // 获取新的数据库管理器实例
                dbManager = DBCipherManager.getInstance(this, config);
                sqlUtilManager = dbManager.getSqlUtilManager();
                tableManager = dbManager.getTableManager();
                
                // 测试连接
                boolean connected = dbManager.testConnection();
                if (connected) {
                    // 保存数据库配置到SharedPreferences
                    android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
                    prefs.edit()
                            .putString("db_name", dbName)
                            .putString("db_password", password)
                            .apply();
                    
                    logMessage("数据库创建成功: " + dbName);
                    AppLogger.i("DatabaseDemo", "数据库配置已保存: " + dbName);
                } else {
                    logMessage("数据库连接失败");
                }
                
            } catch (Exception e) {
                logMessage("创建数据库失败: " + e.getMessage());
            }
        });
    }

    /**
     * 创建表
     */
    private void createTable() {
        runInBackground(() -> {
            try {
                if (tableManager == null) {
                    logMessage("错误: 表管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                // 创建玩家数据表
                String schema = "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                               "player_name TEXT NOT NULL, " +
                               "level INTEGER DEFAULT 1, " +
                               "experience INTEGER DEFAULT 0, " +
                               "gold INTEGER DEFAULT 0, " +
                               "create_time TEXT DEFAULT CURRENT_TIMESTAMP";
                
                boolean success = tableManager.createTableIfNotExists(tableName, schema);
                if (success) {
                    logMessage("表创建成功: " + tableName);
                } else {
                    logMessage("表创建失败");
                }
                
            } catch (Exception e) {
                logMessage("创建表失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "createTable error: " + e.getMessage());
            }
        });
    }

    /**
     * 插入数据
     */
    private void insertData() {
        runInBackground(() -> {
            try {
                if (dbManager == null) {
                    logMessage("错误: 数据库管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                // 创建示例数据
                ContentValues values = new ContentValues();
                values.put("player_name", "测试玩家" + System.currentTimeMillis());
                values.put("level", 1);
                values.put("experience", 0);
                values.put("gold", 1000);
                
                long result = dbManager.insertData(tableName, values);
                if (result != -1) {
                    logMessage("数据插入成功，ID: " + result);
                } else {
                    logMessage("数据插入失败");
                }
                
            } catch (Exception e) {
                logMessage("插入数据失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "insertData error: " + e.getMessage());
            }
        });
    }

    /**
     * 查询数据
     */
    private void queryData() {
        runInBackground(() -> {
            try {
                if (dbManager == null) {
                    logMessage("错误: 数据库管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                List<ContentValues> results = dbManager.queryAll(tableName);
                if (results != null && !results.isEmpty()) {
                    logMessage("查询到 " + results.size() + " 条记录:");
                    for (int i = 0; i < Math.min(results.size(), 5); i++) {
                        ContentValues row = results.get(i);
                        logMessage("记录 " + (i + 1) + ": " + row.toString());
                    }
                    if (results.size() > 5) {
                        logMessage("... 还有 " + (results.size() - 5) + " 条记录");
                    }
                } else {
                    logMessage("未查询到数据");
                }
                
            } catch (Exception e) {
                logMessage("查询数据失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "queryData error: " + e.getMessage());
            }
        });
    }

    /**
     * 更新数据
     */
    private void updateData() {
        runInBackground(() -> {
            try {
                if (dbManager == null) {
                    logMessage("错误: 数据库管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                // 更新第一条记录的等级和金币
                ContentValues values = new ContentValues();
                values.put("level", 10);
                values.put("gold", 5000);
                
                int affected = dbManager.updateData(tableName, values, "id = ?", new String[]{"1"});
                if (affected > 0) {
                    logMessage("数据更新成功，影响行数: " + affected);
                } else {
                    logMessage("数据更新失败或未找到匹配记录");
                }
                
            } catch (Exception e) {
                logMessage("更新数据失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "updateData error: " + e.getMessage());
            }
        });
    }

    /**
     * 删除数据
     */
    private void deleteData() {
        runInBackground(() -> {
            try {
                if (dbManager == null) {
                    logMessage("错误: 数据库管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                // 删除ID大于5的记录
                int affected = dbManager.deleteData(tableName, "id > ?", new String[]{"5"});
                logMessage("数据删除完成，影响行数: " + affected);
                
            } catch (Exception e) {
                logMessage("删除数据失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "deleteData error: " + e.getMessage());
            }
        });
    }

    /**
     * 导出JSON
     */
    private void exportJson() {
        runInBackground(() -> {
            try {
                if (sqlUtilManager == null) {
                    logMessage("错误: SQL工具管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                Object tableData = sqlUtilManager.exportTableToJson(tableName);
                if (tableData != null) {
                    logMessage("表数据导出成功:");
                    logMessage(tableData.toString());
                } else {
                    logMessage("表数据导出失败");
                }
                
            } catch (Exception e) {
                logMessage("导出JSON失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "exportJson error: " + e.getMessage());
            }
        });
    }

    /**
     * 导入JSON
     */
    private void importJson() {
        runInBackground(() -> {
            try {
                if (sqlUtilManager == null) {
                    logMessage("错误: SQL工具管理器未初始化");
                    return;
                }
                
                String tableName = etTableName.getText().toString().trim();
                if (TextUtils.isEmpty(tableName)) {
                    logMessage("表名不能为空");
                    return;
                }
                
                // 创建示例JSON数据
                JSONArray jsonArray = new JSONArray();
                JSONObject player1 = new JSONObject();
                player1.put("player_name", "导入玩家1");
                player1.put("level", 5);
                player1.put("experience", 2500);
                player1.put("gold", 2000);
                jsonArray.put(player1);
                
                JSONObject player2 = new JSONObject();
                player2.put("player_name", "导入玩家2");
                player2.put("level", 8);
                player2.put("experience", 4000);
                player2.put("gold", 3000);
                jsonArray.put(player2);
                
                int imported = sqlUtilManager.batchInsertDataWithJson(tableName, jsonArray);
                if (imported > 0) {
                    logMessage("JSON数据导入成功，导入记录数: " + imported);
                } else {
                    logMessage("JSON数据导入失败");
                }
                
            } catch (Exception e) {
                logMessage("导入JSON失败: " + e.getMessage());
                AppLogger.e("DatabaseDemo", "importJson error: " + e.getMessage());
            }
        });
    }

    /**
     * 清空日志
     */
    private void clearLog() {
        logBuffer.setLength(0);
        tvLog.setText("日志已清空");
    }

    /**
     * 复制日志
     */
    private void copyLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("日志", logBuffer.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    /**
     * 在后台线程运行任务
     */
    private void runInBackground(Runnable task) {
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logMessage("操作异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 记录日志消息
     */
    private void logMessage(String message) {
        if (logBuffer == null) {
            logBuffer = new StringBuilder();
        }
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }

        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";

        logBuffer.append(logEntry);

        if (tvLog != null) {
            final TextView tv = tvLog;
            mainHandler.post(() -> {
                tv.append(logEntry);
                tv.post(() -> {
                    if (tv.getLayout() != null) {
                        int scrollAmount = tv.getLayout().getLineTop(tv.getLineCount()) - tv.getHeight();
                        if (scrollAmount > 0) {
                            tv.scrollTo(0, scrollAmount);
                        } else {
                            tv.scrollTo(0, 0);
                        }
                    }
                });
            });
        }
        AppLogger.d("DatabaseDemo", message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbManager != null) {
            dbManager.closeAllConnections();
        }
    }
}