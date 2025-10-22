package com.orange.gamesavemanager2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import game.core.DBCipherManager;
import game.core.TableManager;

/**
 * 高级表管理演示
 * - 可视化表结构管理
 * - 支持导入加密/明文数据库
 * - 基于 TableManager 的完整功能
 */
public class AdvancedTableManagementActivity extends AppCompatActivity implements View.OnClickListener {
    
    private static final int REQUEST_IMPORT_DB = 1001;
    
    private TextView tvContent;
    private ScrollView scrollView;
    
    // 数据库管理器
    private DBCipherManager dbManager;
    private TableManager tableManager;
    
    // 当前数据库信息
    private String currentDbName = "advanced_demo";
    private String currentPassword = "demo123";
    
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_table_management);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
            initViews();
            initDatabase();
            showWelcome();
            
            AppLogger.i("AdvancedTableMgmt", "page_open");
        } catch (Exception e) {
            AppLogger.e("AdvancedTableMgmt", "onCreate error: " + e.getMessage());
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initViews() {
        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        // 滚动视图和内容显示
        scrollView = findViewById(R.id.scroll_view);
        tvContent = findViewById(R.id.tv_content);
        
        // 数据库操作按钮
        findViewById(R.id.btn_create_db).setOnClickListener(this);
        findViewById(R.id.btn_import_encrypted_db).setOnClickListener(this);
        findViewById(R.id.btn_import_plain_db).setOnClickListener(this);
        findViewById(R.id.btn_show_db_info).setOnClickListener(this);
        
        // 表结构管理按钮
        findViewById(R.id.btn_create_table).setOnClickListener(this);
        findViewById(R.id.btn_show_table_structure).setOnClickListener(this);
        findViewById(R.id.btn_add_column).setOnClickListener(this);
        findViewById(R.id.btn_modify_column_type).setOnClickListener(this);
        findViewById(R.id.btn_drop_table).setOnClickListener(this);
        
        // 高级功能按钮
        findViewById(R.id.btn_batch_add_columns).setOnClickListener(this);
        findViewById(R.id.btn_show_all_tables).setOnClickListener(this);
        findViewById(R.id.btn_export_table_json).setOnClickListener(this);
        findViewById(R.id.btn_clear_log).setOnClickListener(this);
    }
    
    private void initDatabase() {
        runInBackground(() -> {
            try {
                // 从SharedPreferences读取配置
                android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
                currentDbName = prefs.getString("db_name", "advanced_demo");
                currentPassword = prefs.getString("db_password", "demo123");
                
                game.core.DatabaseConfig config = new game.core.DatabaseConfig.Builder()
                    .setDatabaseName(currentDbName)
                    .setPassword(currentPassword)
                    .setVersion(1)
                    .setAutoOptimize(true)
                    .build();
                
                dbManager = game.core.DBCipherManager.getInstance(this, config);
                tableManager = dbManager.getTableManager();
                
                File dbPath = getDatabasePath(currentDbName);
                
                appendLog("✓ 数据库管理器初始化成功\n");
                appendLog("当前数据库: " + currentDbName + "\n");
                appendLog("数据库路径: " + dbPath.getAbsolutePath() + "\n\n");
                
                AppLogger.i("AdvancedTableMgmt", "数据库初始化成功: " + currentDbName);
            } catch (Exception e) {
                appendLog("✗ 数据库初始化失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "数据库初始化失败: " + e.getMessage());
            }
        });
    }
    
    private void showWelcome() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 高级表管理演示 ===\n\n");
        sb.append("功能列表:\n\n");
        sb.append("📁 数据库操作:\n");
        sb.append("  • 创建新数据库\n");
        sb.append("  • 导入加密数据库\n");
        sb.append("  • 导入明文数据库\n");
        sb.append("  • 显示数据库信息\n\n");
        sb.append("📋 表结构管理:\n");
        sb.append("  • 创建表\n");
        sb.append("  • 查看表结构\n");
        sb.append("  • 添加字段\n");
        sb.append("  • 修改字段类型\n");
        sb.append("  • 删除表\n\n");
        sb.append("🔧 高级功能:\n");
        sb.append("  • 批量添加字段\n");
        sb.append("  • 显示所有表\n");
        sb.append("  • 导出表结构JSON\n");
        sb.append("  • 清空日志\n\n");
        sb.append("准备就绪，请选择操作...\n");
        sb.append("─────────────────\n\n");
        
        tvContent.setText(sb.toString());
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        
        if (id == R.id.btn_create_db) {
            showCreateDatabaseDialog();
        } else if (id == R.id.btn_import_encrypted_db) {
            importEncryptedDatabase();
        } else if (id == R.id.btn_import_plain_db) {
            importPlainDatabase();
        } else if (id == R.id.btn_show_db_info) {
            showDatabaseInfo();
        } else if (id == R.id.btn_create_table) {
            showCreateTableDialog();
        } else if (id == R.id.btn_show_table_structure) {
            showTableStructureDialog();
        } else if (id == R.id.btn_add_column) {
            showAddColumnDialog();
        } else if (id == R.id.btn_modify_column_type) {
            showModifyColumnDialog();
        } else if (id == R.id.btn_drop_table) {
            showDropTableDialog();
        } else if (id == R.id.btn_batch_add_columns) {
            showBatchAddColumnsDialog();
        } else if (id == R.id.btn_show_all_tables) {
            showAllTables();
        } else if (id == R.id.btn_export_table_json) {
            showExportTableDialog();
        } else if (id == R.id.btn_clear_log) {
            clearLog();
        }
    }
    
    // ==================== 数据库操作 ====================
    
    private void showCreateDatabaseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建新数据库");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("数据库名称");
        nameInput.setText(currentDbName);
        layout.addView(nameInput);
        
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("密码");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setText(currentPassword);
        layout.addView(passwordInput);
        
        builder.setView(layout);
        builder.setPositiveButton("创建", (dialog, which) -> {
            String dbName = nameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            
            if (dbName.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            createDatabase(dbName, password);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void createDatabase(String dbName, String password) {
        runInBackground(() -> {
            try {
                appendLog("正在创建数据库: " + dbName + "\n");
                
                // 关闭当前数据库
                if (dbManager != null) {
                    dbManager.closeAllConnections();
                }
                
                game.core.DatabaseConfig config = new game.core.DatabaseConfig.Builder()
                    .setDatabaseName(dbName)
                    .setPassword(password)
                    .setVersion(1)
                    .setAutoOptimize(true)
                    .build();
                
                dbManager = game.core.DBCipherManager.getInstance(this, config);
                tableManager = dbManager.getTableManager();
                currentDbName = dbName;
                currentPassword = password;
                
                File dbPath = getDatabasePath(dbName);
                
                // 保存到SharedPreferences
                android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
                prefs.edit()
                    .putString("db_name", dbName)
                    .putString("db_password", password)
                    .apply();
                
                appendLog("✓ 数据库创建成功!\n");
                appendLog("数据库: " + dbName + "\n");
                appendLog("路径: " + dbPath.getAbsolutePath() + "\n\n");
                
                AppLogger.i("AdvancedTableMgmt", "创建数据库成功: " + dbName);
                runOnUiThread(() -> Toast.makeText(this, "数据库创建成功", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                appendLog("✗ 数据库创建失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "创建数据库失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    
    private void importEncryptedDatabase() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择加密数据库文件"), REQUEST_IMPORT_DB);
    }
    
    private void importPlainDatabase() {
        Toast.makeText(this, "请先选择明文数据库文件，然后输入新密码进行加密导入", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择明文数据库文件"), REQUEST_IMPORT_DB + 1);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == REQUEST_IMPORT_DB) {
                    showImportEncryptedDbDialog(uri);
                } else if (requestCode == REQUEST_IMPORT_DB + 1) {
                    showImportPlainDbDialog(uri);
                }
            }
        }
    }
    
    private void showImportEncryptedDbDialog(Uri sourceUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("导入加密数据库");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("原数据库密码");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("新数据库名称");
        nameInput.setText("imported_db");
        layout.addView(nameInput);
        
        builder.setView(layout);
        builder.setPositiveButton("导入", (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            String newName = nameInput.getText().toString().trim();
            
            if (password.isEmpty() || newName.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            importEncryptedDb(sourceUri, newName, password);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showImportPlainDbDialog(Uri sourceUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("导入明文数据库（加密）");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("新数据库名称");
        nameInput.setText("imported_plain_db");
        layout.addView(nameInput);
        
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("新数据库密码");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);
        
        builder.setView(layout);
        builder.setPositiveButton("导入并加密", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            
            if (newName.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            importPlainDb(sourceUri, newName, password);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void importEncryptedDb(Uri sourceUri, String newDbName, String password) {
        runInBackground(() -> {
            try {
                appendLog("正在导入加密数据库...\n");
                
                // 复制文件到应用数据库目录
                File targetFile = getDatabasePath(newDbName);
                copyFile(sourceUri, targetFile);
                
                appendLog("文件已复制到: " + targetFile.getAbsolutePath() + "\n");
                
                // 尝试打开数据库验证密码
                net.sqlcipher.database.SQLiteDatabase testDb = 
                    net.sqlcipher.database.SQLiteDatabase.openDatabase(
                        targetFile.getAbsolutePath(),
                        password,
                        null,
                        net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                    );
                
                testDb.close();
                appendLog("✓ 数据库密码验证成功\n");
                
                // 重新初始化
                currentDbName = newDbName;
                currentPassword = password;
                initDatabase();
                
                appendLog("✓ 导入成功!\n\n");
                AppLogger.i("AdvancedTableMgmt", "导入加密数据库成功: " + newDbName);
                runOnUiThread(() -> Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                appendLog("✗ 导入失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "导入加密数据库失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    
    private void importPlainDb(Uri sourceUri, String newDbName, String password) {
        runInBackground(() -> {
            try {
                appendLog("正在导入明文数据库并加密...\n");
                
                // 先复制到临时文件
                File tempPlainFile = new File(getCacheDir(), "temp_plain.db");
                copyFile(sourceUri, tempPlainFile);
                appendLog("临时文件: " + tempPlainFile.getAbsolutePath() + "\n");
                
                // 打开明文数据库
                android.database.sqlite.SQLiteDatabase plainDb = 
                    android.database.sqlite.SQLiteDatabase.openDatabase(
                        tempPlainFile.getAbsolutePath(),
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    );
                
                // 创建加密数据库
                File encryptedFile = getDatabasePath(newDbName);
                net.sqlcipher.database.SQLiteDatabase encryptedDb = 
                    net.sqlcipher.database.SQLiteDatabase.openOrCreateDatabase(
                        encryptedFile,
                        password,
                        null
                    );
                
                appendLog("正在复制表结构和数据...\n");
                
                // 获取所有表
                android.database.Cursor tablesCursor = plainDb.rawQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
                );
                
                int tableCount = 0;
                int totalRows = 0;
                
                encryptedDb.beginTransaction();
                try {
                    while (tablesCursor.moveToNext()) {
                        String tableName = tablesCursor.getString(0);
                        String createSql = tablesCursor.getString(1);
                        
                        appendLog("  复制表: " + tableName + "\n");
                        
                        // 创建表
                        encryptedDb.execSQL(createSql);
                        
                        // 复制数据
                        android.database.Cursor dataCursor = plainDb.rawQuery("SELECT * FROM " + tableName, null);
                        int rowCount = 0;
                        
                        while (dataCursor.moveToNext()) {
                            android.content.ContentValues values = new android.content.ContentValues();
                            
                            for (int i = 0; i < dataCursor.getColumnCount(); i++) {
                                String columnName = dataCursor.getColumnName(i);
                                int type = dataCursor.getType(i);
                                
                                if (type == android.database.Cursor.FIELD_TYPE_NULL) {
                                    values.putNull(columnName);
                                } else if (type == android.database.Cursor.FIELD_TYPE_INTEGER) {
                                    values.put(columnName, dataCursor.getLong(i));
                                } else if (type == android.database.Cursor.FIELD_TYPE_FLOAT) {
                                    values.put(columnName, dataCursor.getDouble(i));
                                } else {
                                    values.put(columnName, dataCursor.getString(i));
                                }
                            }
                            
                            encryptedDb.insert(tableName, null, values);
                            rowCount++;
                        }
                        dataCursor.close();
                        
                        appendLog("    复制了 " + rowCount + " 行\n");
                        tableCount++;
                        totalRows += rowCount;
                    }
                    
                    encryptedDb.setTransactionSuccessful();
                } finally {
                    encryptedDb.endTransaction();
                    tablesCursor.close();
                }
                
                plainDb.close();
                encryptedDb.close();
                tempPlainFile.delete();
                
                appendLog("\n✓ 导入完成!\n");
                appendLog("表数量: " + tableCount + "\n");
                appendLog("总行数: " + totalRows + "\n\n");
                
                // 重新初始化
                currentDbName = newDbName;
                currentPassword = password;
                initDatabase();
                
                final int finalTableCount = tableCount;
                AppLogger.i("AdvancedTableMgmt", "导入明文数据库成功: " + newDbName + ", " + finalTableCount + " 表, " + totalRows + " 行");
                runOnUiThread(() -> Toast.makeText(this, "导入成功: " + finalTableCount + " 个表", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                appendLog("✗ 导入失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "导入明文数据库失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    
    private void copyFile(Uri sourceUri, File targetFile) throws Exception {
        try (FileInputStream in = (FileInputStream) getContentResolver().openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();
        }
    }
    
    private void showDatabaseInfo() {
        runInBackground(() -> {
            try {
                appendLog("=== 数据库信息 ===\n\n");
                appendLog("数据库名: " + currentDbName + "\n");
                
                File dbFile = getDatabasePath(currentDbName);
                if (dbFile.exists()) {
                    appendLog("文件大小: " + formatFileSize(dbFile.length()) + "\n");
                    appendLog("文件路径: " + dbFile.getAbsolutePath() + "\n");
                }
                
                // 获取表数量
                List<String> tables = tableManager.getAllTableNames();
                appendLog("表数量: " + tables.size() + "\n\n");
                
                if (!tables.isEmpty()) {
                    appendLog("表列表:\n");
                    for (String tableName : tables) {
                        long count = dbManager.queryCount(tableName, null, null);
                        appendLog("  • " + tableName + " (" + count + " 行)\n");
                    }
                }
                
                appendLog("\n");
                AppLogger.i("AdvancedTableMgmt", "显示数据库信息: " + tables.size() + " 个表");
                
            } catch (Exception e) {
                appendLog("✗ 获取数据库信息失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "获取数据库信息失败: " + e.getMessage());
            }
        });
    }
    
    // ==================== 表结构管理 ====================
    
    private void showCreateTableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建表");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("表名");
        layout.addView(nameInput);
        
        final EditText columnsInput = new EditText(this);
        columnsInput.setHint("字段定义（用逗号分隔），如: id INTEGER PRIMARY KEY, name TEXT, age INTEGER");
        columnsInput.setMinLines(3);
        layout.addView(columnsInput);
        
        builder.setView(layout);
        builder.setPositiveButton("创建", (dialog, which) -> {
            String tableName = nameInput.getText().toString().trim();
            String columns = columnsInput.getText().toString().trim();
            
            if (tableName.isEmpty() || columns.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            createTable(tableName, columns);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void createTable(String tableName, String columns) {
        runInBackground(() -> {
            try {
                appendLog("正在创建表: " + tableName + "\n");
                
                // 直接使用字符串schema
                boolean success = tableManager.createTableIfNotExists(tableName, columns);
                
                if (success) {
                    appendLog("✓ 表创建成功!\n\n");
                    AppLogger.i("AdvancedTableMgmt", "创建表成功: " + tableName);
                    runOnUiThread(() -> Toast.makeText(this, "表创建成功", Toast.LENGTH_SHORT).show());
                } else {
                    appendLog("✗ 表创建失败或表已存在\n\n");
                }
                
            } catch (Exception e) {
                appendLog("✗ 创建表失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "创建表失败: " + e.getMessage());
            }
        });
    }
    
    private void showTableStructureDialog() {
        List<String> tables = tableManager.getAllTableNames();
        if (tables.isEmpty()) {
            Toast.makeText(this, "当前数据库没有表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] tableArray = tables.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择表");
        builder.setItems(tableArray, (dialog, which) -> {
            showTableStructure(tableArray[which]);
        });
        builder.show();
    }
    
    private void showTableStructure(String tableName) {
        runInBackground(() -> {
            try {
                appendLog("=== 表结构: " + tableName + " ===\n\n");
                
                List<Map<String, String>> structure = tableManager.getTableStructure(tableName);
                
                if (structure.isEmpty()) {
                    appendLog("无法获取表结构\n\n");
                    return;
                }
                
                appendLog(String.format("%-20s %-15s %-8s %-8s %-15s\n", 
                    "字段名", "类型", "主键", "非空", "默认值"));
                appendLog("─".repeat(70) + "\n");
                
                for (Map<String, String> column : structure) {
                    String name = column.get("name");
                    String type = column.get("type");
                    String pk = "1".equals(column.get("pk")) ? "是" : "";
                    String notnull = "1".equals(column.get("notnull")) ? "是" : "";
                    String dflt = column.get("dflt_value");
                    if (dflt == null || dflt.equals("null")) dflt = "";
                    
                    appendLog(String.format("%-20s %-15s %-8s %-8s %-15s\n",
                        name, type, pk, notnull, dflt));
                }
                
                appendLog("\n");
                AppLogger.i("AdvancedTableMgmt", "显示表结构: " + tableName);
                
            } catch (Exception e) {
                appendLog("✗ 获取表结构失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "获取表结构失败: " + e.getMessage());
            }
        });
    }
    
    private void showAddColumnDialog() {
        List<String> tables = tableManager.getAllTableNames();
        if (tables.isEmpty()) {
            Toast.makeText(this, "当前数据库没有表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] tableArray = tables.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择表");
        builder.setItems(tableArray, (dialog, which) -> {
            showAddColumnInputDialog(tableArray[which]);
        });
        builder.show();
    }
    
    private void showAddColumnInputDialog(String tableName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加字段到: " + tableName);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        final EditText nameInput = new EditText(this);
        nameInput.setHint("字段名");
        layout.addView(nameInput);
        
        final EditText typeInput = new EditText(this);
        typeInput.setHint("字段类型（如: TEXT, INTEGER, REAL）");
        typeInput.setText("TEXT");
        layout.addView(typeInput);
        
        builder.setView(layout);
        builder.setPositiveButton("添加", (dialog, which) -> {
            String columnName = nameInput.getText().toString().trim();
            String columnType = typeInput.getText().toString().trim();
            
            if (columnName.isEmpty() || columnType.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            
            addColumn(tableName, columnName, columnType);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void addColumn(String tableName, String columnName, String columnType) {
        runInBackground(() -> {
            try {
                appendLog("正在为表 '" + tableName + "' 添加字段 '" + columnName + "'...\n");
                
                boolean success = tableManager.addColumnIfNotExists(tableName, columnName, columnType);
                
                if (success) {
                    appendLog("✓ 字段添加成功!\n\n");
                    AppLogger.i("AdvancedTableMgmt", "添加字段成功: " + tableName + "." + columnName);
                    runOnUiThread(() -> Toast.makeText(this, "字段添加成功", Toast.LENGTH_SHORT).show());
                } else {
                    appendLog("✗ 字段添加失败或已存在\n\n");
                }
                
            } catch (Exception e) {
                appendLog("✗ 添加字段失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "添加字段失败: " + e.getMessage());
            }
        });
    }
    
    private void showModifyColumnDialog() {
        Toast.makeText(this, "SQLite不支持直接修改字段类型，需要重建表", Toast.LENGTH_LONG).show();
        // 此功能比较复杂，暂不实现
    }
    
    private void showDropTableDialog() {
        List<String> tables = tableManager.getAllTableNames();
        if (tables.isEmpty()) {
            Toast.makeText(this, "当前数据库没有表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] tableArray = tables.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要删除的表");
        builder.setItems(tableArray, (dialog, which) -> {
            confirmDropTable(tableArray[which]);
        });
        builder.show();
    }
    
    private void confirmDropTable(String tableName) {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除表 '" + tableName + "' 吗？此操作不可恢复！")
            .setPositiveButton("删除", (dialog, which) -> dropTable(tableName))
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void dropTable(String tableName) {
        runInBackground(() -> {
            try {
                appendLog("正在删除表: " + tableName + "\n");
                
                boolean success = tableManager.dropTable(tableName);
                
                if (success) {
                    appendLog("✓ 表删除成功!\n\n");
                    AppLogger.i("AdvancedTableMgmt", "删除表成功: " + tableName);
                    runOnUiThread(() -> Toast.makeText(this, "表删除成功", Toast.LENGTH_SHORT).show());
                } else {
                    appendLog("✗ 表删除失败\n\n");
                }
                
            } catch (Exception e) {
                appendLog("✗ 删除表失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "删除表失败: " + e.getMessage());
            }
        });
    }
    
    // ==================== 高级功能 ====================
    
    private void showBatchAddColumnsDialog() {
        List<String> tables = tableManager.getAllTableNames();
        if (tables.isEmpty()) {
            Toast.makeText(this, "当前数据库没有表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] tableArray = tables.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择表");
        builder.setItems(tableArray, (dialog, which) -> {
            showBatchAddColumnsInputDialog(tableArray[which]);
        });
        builder.show();
    }
    
    private void showBatchAddColumnsInputDialog(String tableName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("批量添加字段到: " + tableName);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        
        TextView hint = new TextView(this);
        hint.setText("每行一个字段，格式: 字段名 类型\n例如:\nage INTEGER\naddress TEXT");
        layout.addView(hint);
        
        final EditText columnsInput = new EditText(this);
        columnsInput.setHint("字段定义（每行一个）");
        columnsInput.setMinLines(5);
        layout.addView(columnsInput);
        
        builder.setView(layout);
        builder.setPositiveButton("添加", (dialog, which) -> {
            String columnsText = columnsInput.getText().toString().trim();
            
            if (columnsText.isEmpty()) {
                Toast.makeText(this, "请输入字段定义", Toast.LENGTH_SHORT).show();
                return;
            }
            
            batchAddColumns(tableName, columnsText);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void batchAddColumns(String tableName, String columnsText) {
        runInBackground(() -> {
            try {
                appendLog("正在批量添加字段到表: " + tableName + "\n");
                
                List<Map<String, String>> columnDefs = new ArrayList<>();
                String[] lines = columnsText.split("\n");
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length >= 2) {
                        Map<String, String> column = new HashMap<>();
                        column.put("name", parts[0]);
                        column.put("type", parts[1]);
                        columnDefs.add(column);
                        appendLog("  添加: " + parts[0] + " " + parts[1] + "\n");
                    }
                }
                
                boolean success = tableManager.batchAddColumns(tableName, columnDefs);
                
                if (success) {
                    appendLog("\n✓ 批量添加字段成功!\n\n");
                    AppLogger.i("AdvancedTableMgmt", "批量添加字段成功: " + tableName + ", " + columnDefs.size() + " 个字段");
                    runOnUiThread(() -> Toast.makeText(this, "批量添加成功", Toast.LENGTH_SHORT).show());
                } else {
                    appendLog("\n✗ 批量添加字段失败\n\n");
                }
                
            } catch (Exception e) {
                appendLog("✗ 批量添加字段失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "批量添加字段失败: " + e.getMessage());
            }
        });
    }
    
    private void showAllTables() {
        runInBackground(() -> {
            try {
                appendLog("=== 所有表列表 ===\n\n");
                
                List<String> tables = tableManager.getAllTableNames();
                
                if (tables.isEmpty()) {
                    appendLog("当前数据库没有表\n\n");
                    return;
                }
                
                appendLog("共 " + tables.size() + " 个表:\n\n");
                
                for (String tableName : tables) {
                    long count = dbManager.queryCount(tableName, null, null);
                    List<Map<String, String>> structure = tableManager.getTableStructure(tableName);
                    
                    // 提取列名
                    List<String> columnNames = new ArrayList<>();
                    for (Map<String, String> column : structure) {
                        columnNames.add(column.get("name"));
                    }
                    
                    appendLog("• " + tableName + "\n");
                    appendLog("  行数: " + count + "\n");
                    appendLog("  字段数: " + columnNames.size() + "\n");
                    appendLog("  字段: " + String.join(", ", columnNames) + "\n\n");
                }
                
                AppLogger.i("AdvancedTableMgmt", "显示所有表: " + tables.size() + " 个");
                
            } catch (Exception e) {
                appendLog("✗ 获取表列表失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "获取表列表失败: " + e.getMessage());
            }
        });
    }
    
    private void showExportTableDialog() {
        List<String> tables = tableManager.getAllTableNames();
        if (tables.isEmpty()) {
            Toast.makeText(this, "当前数据库没有表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] tableArray = tables.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要导出的表");
        builder.setItems(tableArray, (dialog, which) -> {
            exportTableStructureToJson(tableArray[which]);
        });
        builder.show();
    }
    
    private void exportTableStructureToJson(String tableName) {
        runInBackground(() -> {
            try {
                appendLog("正在导出表结构: " + tableName + "\n");
                
                List<Map<String, String>> structure = tableManager.getTableStructure(tableName);
                
                JSONObject json = new JSONObject();
                json.put("table_name", tableName);
                json.put("export_time", System.currentTimeMillis());
                
                JSONArray columns = new JSONArray();
                for (Map<String, String> column : structure) {
                    JSONObject col = new JSONObject();
                    col.put("name", column.get("name"));
                    col.put("type", column.get("type"));
                    col.put("primary_key", "1".equals(column.get("pk")));
                    col.put("not_null", "1".equals(column.get("notnull")));
                    col.put("default_value", column.get("dflt_value"));
                    columns.put(col);
                }
                json.put("columns", columns);
                
                String jsonStr = json.toString(2);
                
                appendLog("\n表结构 JSON:\n");
                appendLog("─".repeat(50) + "\n");
                appendLog(jsonStr + "\n");
                appendLog("─".repeat(50) + "\n\n");
                
                // 保存到文件
                File exportDir = new File(getExternalFilesDir(null), "exports");
                if (!exportDir.exists()) exportDir.mkdirs();
                
                File jsonFile = new File(exportDir, tableName + "_structure.json");
                try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                    fos.write(jsonStr.getBytes());
                }
                
                appendLog("✓ 已保存到: " + jsonFile.getAbsolutePath() + "\n\n");
                
                AppLogger.i("AdvancedTableMgmt", "导出表结构JSON成功: " + tableName);
                runOnUiThread(() -> Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                appendLog("✗ 导出失败: " + e.getMessage() + "\n");
                AppLogger.e("AdvancedTableMgmt", "导出表结构失败: " + e.getMessage());
            }
        });
    }
    
    // ==================== 辅助方法 ====================
    
    private void appendLog(String message) {
        runOnUiThread(() -> {
            tvContent.append(message);
            scrollToBottom();
        });
    }
    
    private void clearLog() {
        tvContent.setText("");
        showWelcome();
        AppLogger.i("AdvancedTableMgmt", "清空日志");
    }
    
    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    private void runInBackground(Runnable task) {
        new Thread(task).start();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbManager != null) {
            dbManager.closeAllConnections();
        }
    }
}

