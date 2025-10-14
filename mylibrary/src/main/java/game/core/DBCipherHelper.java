package game.core;

import java.lang.*;
import android.content.Context;
import android.util.Log;
import android.database.SQLException;
import android.text.TextUtils;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.*;
import android.database.sqlite.SQLiteException;
import android.util.Base64;
import java.io.File;

public class DBCipherHelper extends SQLiteOpenHelper {
	private static final String TAG = "DatabaseHelper";
	private static final int DB_VERSION = 1;   // 数据库版本
	public static final String DB_NAME = "default_db"; // 默认数据库名
	// 日志回调接口和实现
	public interface LogCallback {
		void onLog(int level, String tag, String message, Throwable throwable);
	}
	public static final int LEVEL_VERBOSE = 0;
	public static final int LEVEL_DEBUG = 1;
	public static final int LEVEL_INFO = 2;
	public static final int LEVEL_WARN = 3;
	public static final int LEVEL_ERROR = 4;
	public static final int LEVEL_NONE = 5;
	private static int sCurrentLogLevel = LEVEL_DEBUG; // 默认设置为DEBUG级别
	
	public static void setGlobalLogLevel(int logLevel) {
		sCurrentLogLevel = logLevel;
	}
	
	private volatile LogCallback logCallback;
	private volatile ConnectCallback connectCallback; 
	// 统一的日志记录方法
	private void log(int level, String message) {
		log(level, message, null);
	}
	
	//连接状态回调
	public interface ConnectCallback {
		/**
		* 连接成功回调
		* @param db 成功创建的SQLiteDatabase实例
		*/
		void onSuccess(SQLiteDatabase db);
		/**
		* 连接失败回调
		* @param throwable 失败异常（含具体原因，如密码错、文件无效）
		* @param errorMsg 失败描述（简化的错误信息，便于快速查看）
		*/
		void onFailed(Throwable throwable, String errorMsg);
	}
	private void log(int level, String message, Throwable throwable) {
		if (level < sCurrentLogLevel) {
			return;
		}
		if (logCallback == null) {
			return;
		}
		try {
			logCallback.onLog(level, TAG, message, throwable);
		} catch (Exception e) {
			Log.e(TAG, "日志回调执行失败: " + e.getMessage(), e);
		}
	}
	
	// 设置日志回调的方法
	public void setLogCallback(LogCallback callback) {
		this.logCallback = callback;
	}
	//设置连接的方法回调
	public void setConnectCallback(ConnectCallback callback) {
		this.connectCallback = callback;
	}
	
	/**
	* 指定数据库完整路径的构造函数
	* @param context 上下文
	* @param dbFullPath 数据库文件的完整路径（例如：/sdcard/MyApp/databases/game.db）
	* @param factory 游标工厂，可为null
	* @param version 数据库版本
	*/
	public DBCipherHelper(Context context, String dbFullPath, SQLiteDatabase.CursorFactory factory, int version) {
		super(context, dbFullPath, factory, version);
	}
	
	public DBCipherHelper(Context context) {
		this(context, DB_NAME, null, DB_VERSION);
	}
	
	public DBCipherHelper(Context context, DatabaseConfig config) {
		this(context, config.getDatabaseName(), null, config.getVersion());
	}
	
	
	/**
	* 创建数据库
	* @param db
	*/
	@Override
	public void onCreate(SQLiteDatabase db) {
		// 留空，由connectWithConfig方法动态创建表
	}
	
	/**
	* 使用DatabaseConfig配置创建加密数据库连接
	* @param config 数据库配置信息
	* @return SQLiteDatabase实例，失败返回null
	*/
	public SQLiteDatabase connectWithConfig(DatabaseConfig config) {
		if (config == null || config.getPassword() == null) {
			log(Log.ERROR, "数据库配置或密码为空");
			return null;
		}
		
		SQLiteDatabase database = null;
		try {
			if (TextUtils.isEmpty(config.getDatabaseName())) {
				throw new IllegalArgumentException("数据库名称不能为空");
			}
			
			database = getWritableDatabase(config.getPassword());
			createTablesIfNeeded(database, config.getTableSchemas());
			
			log(Log.INFO, "数据库连接成功: " + config.getDatabaseName());
			
			if(connectCallback!=null)
			{
				connectCallback.onSuccess(database);
			}
			return database;
			
		} catch (SQLException e) {
			log(Log.ERROR, "数据库连接失败: " + e.getMessage(), e);
			if (database != null) {
				database.close();
			}
			if(connectCallback!=null)
			{
				connectCallback.onFailed(e,"数据库连接失败");
			}
			return null;
		} finally {
		}
	}
	
	
	/**
	* 根据配置创建或更新数据表
	* 改造点1：表已存在时，先剔除schema中的外键约束，再更新表结构
	*/
	public void createTablesIfNeeded(SQLiteDatabase db, Map<String, String> tableSchemas) {
		if (tableSchemas == null || tableSchemas.isEmpty()) {
			log(Log.DEBUG, "表结构配置为空，跳过表创建");
			return;
		}
		
		for (Map.Entry<String, String> entry : tableSchemas.entrySet()) {
			String tableName = entry.getKey();
			String originalSchema = entry.getValue(); // 原始schema（含外键）
			String schemaToUse = originalSchema; // 待使用的schema（默认用原始，建表时保留外键）
			
			try {
				// 检查表是否存在
				Cursor cursor = db.rawQuery(
				"SELECT name FROM sqlite_master WHERE type='table' AND name=?",
				new String[]{tableName});
				boolean tableExists = cursor.getCount() > 0;
				cursor.close();
				
				if (!tableExists) {
					// 表不存在：用原始schema（含外键），正常建表（外键仅此时生效）
					String createSQL = "CREATE TABLE " + tableName + " (" + schemaToUse + ");";
					db.execSQL(createSQL);
					log(Log.INFO, "表创建成功（含外键约束）: " + tableName);
				} else {
					// 表已存在：调用工具方法剔除外键，仅用普通列schema更新表
					schemaToUse = removeForeignKeyFromSchema(originalSchema);
					updateTableSchema(db, tableName, schemaToUse);
					log(Log.INFO, "表已存在，跳过外键约束，仅更新普通列: " + tableName);
				}
			} catch (Exception e) {
				log(Log.ERROR, "处理表失败: " + tableName, e);
			}
		}
	}
	
	/**
	* 剔除schema中的外键约束
	* 原理：识别含"FOREIGN KEY"的语句段，过滤后重新拼接普通列schema
	*/
	private String removeForeignKeyFromSchema(String originalSchema) {
		if (TextUtils.isEmpty(originalSchema) || !originalSchema.contains("FOREIGN KEY")) {
			// 无外键，直接返回原始schema
			return originalSchema;
		}
		
		List<String> normalColumns = new ArrayList<>();
		// 按逗号分割schema（外键通常是单独一段，或跟在列后用逗号分隔）
		String[] schemaParts = originalSchema.split(",");
		
		for (String part : schemaParts) {
			part = part.trim();
			if (part.isEmpty()) {
				continue;
			}
			// 过滤掉包含外键的片段（不区分大小写，避免拼写差异）
			if (!part.toUpperCase().contains("FOREIGN KEY")) {
				normalColumns.add(part);
			} else {
				// 日志提示：已跳过外键
				log(Log.DEBUG, "已跳过外键约束: " + part);
			}
		}
		
		// 重新拼接普通列schema（还原逗号分隔格式）
		return TextUtils.join(", ", normalColumns);
	}
	
	/**
	* 更新已存在表的表结构（添加缺失字段）
	* 入参已变为“剔除外键后的schema”，无需额外修改，直接复用原有逻辑
	*/
	private void updateTableSchema(SQLiteDatabase db, String tableName, String newSchema) {
		try {
			List<String> existingColumns = getExistingColumns(db, tableName);
			List<ColumnDefinition> newColumnDefs = parseColumnDefinitions(newSchema);
			
			for (ColumnDefinition newColumn : newColumnDefs) {
				if (!existingColumns.contains(newColumn.name)) {
					String alterSQL = "ALTER TABLE " + tableName + " ADD COLUMN " +
					newColumn.name + " " + newColumn.type;
					
					if (newColumn.constraints != null && !newColumn.constraints.isEmpty()) {
						alterSQL += " " + newColumn.constraints;
					}
					
					db.execSQL(alterSQL);
					log(Log.INFO, "表 " + tableName + " 添加新列: " + newColumn.name);
				}
			}
			
		} catch (Exception e) {
			log(Log.ERROR, "更新表结构失败: " + tableName, e);
		}
	}
	
	/**
	* 获取已存在表的所有列名
	*/
	private List<String> getExistingColumns(SQLiteDatabase db, String tableName) {
		List<String> columns = new ArrayList<>();
		Cursor cursor = null;
		
		try {
			cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
			
			while (cursor != null && cursor.moveToNext()) {
				String columnName = cursor.getString(cursor.getColumnIndex("name"));
				columns.add(columnName);
			}
		} catch (Exception e) {
			log(Log.ERROR, "获取表列信息失败: " + tableName, e);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return columns;
	}
	
	/**
	* 解析表结构SQL
	*/
	private List<ColumnDefinition> parseColumnDefinitions(String schema) {
		List<ColumnDefinition> columnDefs = new ArrayList<>();
		
		try {
			String[] columnParts = schema.split(",");
			
			for (String part : columnParts) {
				part = part.trim();
				if (part.isEmpty()) continue;
				
				ColumnDefinition colDef = new ColumnDefinition();
				String[] tokens = part.split("\\s+");
				
				if (tokens.length >= 2) {
					colDef.name = tokens[0].trim();
					colDef.type = tokens[1].trim();
					
					if (tokens.length > 2) {
						StringBuilder constraints = new StringBuilder();
						for (int i = 2; i < tokens.length; i++) {
							constraints.append(tokens[i]).append(" ");
						}
						colDef.constraints = constraints.toString().trim();
					}
					
					columnDefs.add(colDef);
				}
			}
		} catch (Exception e) {
			log(Log.ERROR, "解析表结构失败: " + schema, e);
		}
		
		return columnDefs;
	}
	
	/**
	* 数据库升级
	*/
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		log(Log.INFO, "数据库升级从版本 " + oldVersion + " 到 " + newVersion);
	}
	
	/**
	* 列定义辅助类
	*/
	private static class ColumnDefinition {
		String name;
		String type;
		String constraints;
	}
	
	/**
	* 修改数据库密码
	*/
	public boolean changePassword(String oldPassword, String newPassword) {
		if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword)) {
			log(Log.ERROR, "旧密码或新密码为空");
			return false;
		}
		
		SQLiteDatabase db = null;
		try {
			db = getWritableDatabase(oldPassword);
			Cursor cursor = db.rawQuery("SELECT count(*) FROM sqlite_master", null);
			if (cursor != null) {
				cursor.moveToFirst();
				cursor.close();
			}
			
			db.execSQL("PRAGMA rekey = ?", new Object[]{newPassword});
			log(Log.INFO, "数据库密码修改成功");
			return true;
			
		} catch (SQLiteException e) {
			if (e.getMessage().contains("file is encrypted or is not a database")) {
				log(Log.ERROR, "旧密码不正确，无法修改密码");
			} else {
				log(Log.ERROR, "数据库操作异常: " + e.getMessage(), e);
			}
			return false;
		} catch (Exception e) {
			log(Log.ERROR, "修改数据库密码失败: " + e.getMessage(), e);
			return false;
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}
	
	/**
	* 获取当前日志回调实例
	*/
	public LogCallback getLogCallback() {
		return logCallback;
	}
	
	/**
	* 获取表结构信息
	*/
	public List<Map<String, String>> getTableStructure(SQLiteDatabase db, String tableName) {
		List<Map<String, String>> columns = new ArrayList<>();
		Cursor cursor = null;
		
		try {
			cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
			
			if (cursor != null) {
				while (cursor.moveToNext()) {
					Map<String, String> columnInfo = new HashMap<>();
					columnInfo.put("cid", cursor.getString(cursor.getColumnIndexOrThrow("cid")));
					columnInfo.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));
					columnInfo.put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")));
					columnInfo.put("notnull", cursor.getString(cursor.getColumnIndexOrThrow("notnull")));
					columnInfo.put("dflt_value", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")));
					columnInfo.put("pk", cursor.getString(cursor.getColumnIndexOrThrow("pk")));
					
					columns.add(columnInfo);
				}
			}
		} finally {
			if (cursor != null) cursor.close();
		}
		return columns;
	}
	
	/**
	* 安全删除数据库及其所有相关文件
	*/
	public boolean deleteDatabase(Context context, String dbPath) {
		if (TextUtils.isEmpty(dbPath)) {
			Log.e(TAG, "数据库路径为空");
			return false;
		}
		
		File dbFile = new File(dbPath);
		boolean mainDeleted = deleteFileSafely(dbFile);
		boolean walDeleted = deleteFileSafely(new File(dbPath + "-wal"));
		boolean shmDeleted = deleteFileSafely(new File(dbPath + "-shm"));
		boolean journalDeleted = deleteFileSafely(new File(dbPath + "-journal"));
		
		return mainDeleted || walDeleted || shmDeleted || journalDeleted;
	}
	
	/**
	* 安全删除单个文件
	*/
	private boolean deleteFileSafely(File file) {
		if (file != null && file.exists()) {
			for (int i = 0; i < 3; i++) {
				if (file.delete()) {
					return true;
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			log(Log.ERROR, "无法删除文件: " + file.getAbsolutePath(), null);
		}
		return false;
	}
	
}