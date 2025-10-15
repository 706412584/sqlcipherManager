package game.core;

import android.content.ContentValues;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.database.SQLException;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import net.sqlcipher.database.SQLiteStatement;
import java.util.Iterator;
import java.util.Set;
/**
* 数据库管理者 - 提供多数据库管理封装
* 使用SQLCipher对数据库进行加密，支持动态表名操作、事务管理、表结构管理和可配置的日志系统
*
* 重要提示：所有数据库操作均需提供正确的密码才能访问加密数据库
*/
public class DBCipherManager {
	private static final String TAG = "DatabaseManager";
	
	// 多数据库实例管理器（数据库名称 -> DBCipherManager实例）
	private static final ConcurrentHashMap<String, DBCipherManager> instances = new ConcurrentHashMap<>();
	private final TableManager tableManager;//表结构管理器
	private final SqlUtilManager sqlUtilManager;//工具类辅助
	private final NumericFieldUpdater numericFieldUpdater;//数值操作工具
	// 数据库帮助类
	private DBCipherHelper dbHelper;
	// 数据库配置
	private DatabaseConfig currentConfig;
	// 应用上下文
	private Context mContext;
	// 数据库名称OR数据库地址
	private String databaseName;
	
	// 日志级别枚举
	public enum LogLevel {
		VERBOSE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), NONE(5);
		
		private int value;
		LogLevel(int value) { this.value = value; }
		public int getValue() { return value; }
	}
	
	// 日志回调接口
	public interface LogCallback {
		void onLog(LogLevel level, String tag, String message, Throwable throwable);
	}
	
	// 当前日志级别（默认ERROR级别）
	private static LogLevel currentLogLevel = LogLevel.ERROR;
	// 日志回调实例
	private static LogCallback logCallback;
	// 是否启用控制台日志（默认启用）
	private static boolean enableConsoleLog = true;
	
	// 使用线程本地存储管理连接
	private ThreadLocal<SQLiteDatabase> threadLocalConnection = new ThreadLocal<>();
	// 连接引用计数器
	private ThreadLocal<Integer> connectionRefCount = new ThreadLocal<>();
	/**
	* 私有构造函数，初始化DatabaseHelper
	* @param context 应用上下文，用于初始化SQLCipher库和创建Helper
	* @param config 数据库配置
	*/
	private DBCipherManager(Context context, DatabaseConfig config) {
		this.mContext = context.getApplicationContext();
		this.currentConfig = config;
		this.databaseName = config.getDatabaseName();
		this.tableManager = new TableManager(this);
		this.numericFieldUpdater=new NumericFieldUpdater(this);
        this.sqlUtilManager = new SqlUtilManager(this);
		dbHelper = new DBCipherHelper(this.mContext, config);
		dbHelper.setLogCallback(new DBCipherHelper.LogCallback() {
			@Override
			public void onLog(int level, String tag, String message, Throwable throwable) {
				LogLevel mappedLevel = mapLogLevel(level);
				log(mappedLevel, tag, message, throwable);
			}
		});
	}
	
	private LogLevel mapLogLevel(int androidLogLevel) {
		switch (androidLogLevel) {
			case Log.VERBOSE: return LogLevel.VERBOSE;
			case Log.DEBUG: return LogLevel.DEBUG;
			case Log.INFO: return LogLevel.INFO;
			case Log.WARN: return LogLevel.WARN;
			case Log.ERROR: return LogLevel.ERROR;
			default: return LogLevel.DEBUG;
		}
	}
	
	// ==================== 多数据库管理 ====================
	
	/**
	* 获取数据库管理器实例（支持多数据库）
	* @param context 应用上下文
	* @param config 数据库配置
	* @return DBCipherManager 实例
	*/
	public static DBCipherManager getInstance(Context context, DatabaseConfig config) {
		String dbName = config.getDatabaseName();
		DBCipherManager instance = instances.get(dbName);
		
		if (instance == null) {
			synchronized (DBCipherManager.class) {
				instance = instances.get(dbName);
				if (instance == null) {
					instance = new DBCipherManager(context, config);
					instances.put(dbName, instance);
				}
			}
		}
		return instance;
	}
	
	/**
	* 获取表管理器实例
	*/
	public TableManager getTableManager() {
		return tableManager;
	}
	
	
	public SqlUtilManager getSqlUtilManager() {
		return sqlUtilManager;
	}
	
	public NumericFieldUpdater getNumericFieldUpdater() {
		return numericFieldUpdater;
	}
	
	
	/**
	* 移除数据库管理器实例
	* @param dbName 数据库名称
	*/
	public static void removeInstance(String dbName) {
		DBCipherManager instance = instances.remove(dbName);
		if (instance != null) {
			instance.closeAllConnections();
		}
	}
	
	/**
	* 获取当前数据库名称
	* @return 数据库名称
	*/
	public String getDatabaseName() {
		return databaseName;
	}
	
	// ==================== 数据库连接管理 ====================
	
	/**
	* 设置数据库配置
	* @param config 数据库配置
	*/
	public void setDatabaseConfig(DatabaseConfig config) {
		this.currentConfig = config;
		dbHelper = new DBCipherHelper(this.mContext, config);
	}
	
	/**
	* 获取数据库连接（线程安全）
	*/
	public synchronized SQLiteDatabase getConnection() {
		SQLiteDatabase db = threadLocalConnection.get();
		if (db == null || !db.isOpen()) {
			db = dbHelper.connectWithConfig(currentConfig);
			threadLocalConnection.set(db);
			connectionRefCount.set(1); // 初始化引用计数
			log(LogLevel.DEBUG, TAG, "创建新数据库连接", null);
		} else {
			// 增加引用计数
			int count = connectionRefCount.get();
			connectionRefCount.set(count + 1);
			log(LogLevel.DEBUG, TAG, "重用数据库连接，引用计数: " + (count + 1), null);
		}
		return db;
	}
	
	/**
	* 释放数据库连接（减少引用计数）
	*/
	public synchronized void releaseConnection() {
		Integer count = connectionRefCount.get();
		if (count == null || count <= 1) {
			// 当引用计数为1或不存在时，关闭连接
			SQLiteDatabase db = threadLocalConnection.get();
			if (db != null && db.isOpen()) {
				try {
					db.close();
					log(LogLevel.DEBUG, TAG, "关闭数据库连接", null);
				} catch (Exception e) {
					log(LogLevel.ERROR, TAG, "关闭连接失败", e);
				}
			}
			threadLocalConnection.remove();
			connectionRefCount.remove();
		} else {
			// 减少引用计数
			connectionRefCount.set(count - 1);
			log(LogLevel.DEBUG, TAG, "减少连接引用计数: " + (count - 1), null);
		}
	}
	
	/**
	* 关闭所有连接
	*/
	public void closeAllConnections() {
		SQLiteDatabase db = threadLocalConnection.get();
		if (db != null && db.isOpen()) {
			try {
				db.close();
				log(LogLevel.INFO, TAG, "关闭所有数据库连接", null);
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "关闭连接失败", e);
			}
		}
		threadLocalConnection.remove();
		connectionRefCount.remove();
	}
	
	/**
	* 安全执行数据库操作（自动管理连接）
	*/
	public <T> T executeWithConnection(DatabaseOperation<T> operation) {
		SQLiteDatabase db = getConnection();
		try {
			return operation.execute(db);
		} finally {
			releaseConnection();
		}
	}
	
	public interface DatabaseOperation<T> {
		T execute(SQLiteDatabase db);
	}
	
	// ==================== 事务管理 ====================
	
	/**
	* 执行事务操作
	*/
	public void executeTransaction(TransactionRunnable transaction) {
		executeWithConnection(db -> {
			db.beginTransaction();
			try {
				transaction.run(db);
				db.setTransactionSuccessful();
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "事务执行失败", e);
			} finally {
				db.endTransaction();
			}
			return null; // 无返回值
		});
	}
	
	public interface TransactionRunnable {
		void run(SQLiteDatabase db);
	}
	
	// ==================== 密码管理 ====================
	
	/**
	* 修改数据库密码
	* @param newPassword 新密码
	* @return true修改成功，false修改失败
	*/
	public boolean changePassword(String newPassword) {
		return dbHelper.changePassword(currentConfig.getPassword().toString(), newPassword);
	}
	
	// ==================== 日志系统 ====================
	
	/**
	* 设置日志级别
	* @param level 日志级别，低于此级别的日志将被过滤
	*/
	public void setLogLevel(LogLevel level) {
		currentLogLevel = level;
		log(LogLevel.INFO, TAG, "日志级别设置为: " + level.toString(), null);
	}
	
	/**
	* 设置日志回调接口，用于自定义日志处理
	* @param callback 日志回调实例
	*/
	public static void setLogCallback(LogCallback callback) {
		logCallback = callback;
		log(LogLevel.INFO, TAG, "日志回调接口已设置", null);
	}
	
	/**
	* 启用或禁用控制台日志输出
	* @param enable true启用，false禁用
	*/
	public void setEnableConsoleLog(boolean enable) {
		enableConsoleLog = enable;
		log(LogLevel.INFO, TAG, "控制台日志输出" + (enable ? "启用" : "禁用"), null);
	}
	
	/**
	* 内部日志记录方法
	* @param level 日志级别
	* @param tag 日志标签
	* @param message 日志消息
	* @param throwable 异常信息（可为null）
	*/
	public static void log(LogLevel level, String tag, String message, Throwable throwable) {
		// 检查日志级别过滤
		if (level.getValue() < currentLogLevel.getValue()) {
			return;
		}
		
		// 控制台日志输出
		if (enableConsoleLog) {
			switch (level) {
				case VERBOSE:
				Log.v(tag, message, throwable);
				break;
				case DEBUG:
				Log.d(tag, message, throwable);
				break;
				case INFO:
				Log.i(tag, message, throwable);
				break;
				case WARN:
				Log.w(tag, message, throwable);
				break;
				case ERROR:
				Log.e(tag, message, throwable);
				break;
			}
		}
		
		// 回调接口处理
		if (logCallback != null) {
			try {
				logCallback.onLog(level, tag, message, throwable);
			} catch (Exception e) {
				// 防止回调异常导致系统崩溃
				Log.e(TAG, "日志回调处理异常: " + e.getMessage());
			}
		}
	}
	
	// ==================== 数据操作 - 插入 ====================
	
	/**
	* 插入单条数据到指定表（支持ContentValues和JSON格式）
	* @param tableName 要操作的表名
	* @param values ContentValues对象或通过jsonToContentValues转换的对象
	* @return 插入的行ID，-1表示插入失败
	*/
	public long insertData(String tableName, ContentValues values) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "开始插入数据到表: " + tableName + ", 字段数: " + values.size(), null);
			
			long result = -1;
			
			try {
				result = db.insert(tableName, null, values);
				
				if (result != -1) {
					log(LogLevel.INFO, TAG, "数据插入成功，行ID: " + result, null);
				} else {
					log(LogLevel.ERROR, TAG, "数据插入失败：数据库连接正常，可能是约束冲突或数据格式问题", null);
				}
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "插入数据时发生SQL异常: " + e.getMessage(), e);
				
				// 根据常见错误信息提供更具体的提示
				if (e.getMessage().contains("no such table")) {
					log(LogLevel.ERROR, TAG, "表不存在: " + tableName, null);
				} else if (e.getMessage().contains("has no column")) {
					log(LogLevel.ERROR, TAG, "字段不存在，请检查字段名是否正确", null);
				} else if (e.getMessage().contains("NOT NULL constraint failed")) {
					log(LogLevel.ERROR, TAG, "违反非空约束，请检查必填字段", null);
				} else if (e.getMessage().contains("UNIQUE constraint failed")) {
					log(LogLevel.ERROR, TAG, "违反唯一约束，可能存在重复数据", null);
				}
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "插入数据时发生未知异常: " + e.getMessage(), e);
			}
			return result;
		});
	}
	/**
	* 插入单条数据到指定表（原生Sql格式）
	* @param tableName 要操作的表名
	* @param values ContentValues对象或通过jsonToContentValues转换的对象
	* @return 插入的行ID，-1表示插入失败
	*/
	public long insertDataWithDetail(String tableName, ContentValues values) {
		return executeWithConnection(db -> {
			try {
				// 构建INSERT语句
				StringBuilder sqlBuilder = new StringBuilder("INSERT INTO ");
				sqlBuilder.append(tableName).append(" (");
				
				// 构建字段名部分
				Set<String> keys = values.keySet();
				Iterator<String> iterator = keys.iterator();
				while (iterator.hasNext()) {
					sqlBuilder.append(iterator.next());
					if (iterator.hasNext()) sqlBuilder.append(", ");
				}
				
				sqlBuilder.append(") VALUES (");
				// 构建值部分（使用占位符）
				iterator = keys.iterator();
				while (iterator.hasNext()) {
					sqlBuilder.append("?");
					if (iterator.hasNext()) sqlBuilder.append(", ");
				}
				sqlBuilder.append(")");
				
				String sql = sqlBuilder.toString();
				log(LogLevel.DEBUG, TAG, "执行SQL: " + sql, null);
				
				// 执行原生SQL语句
				SQLiteStatement statement = db.compileStatement(sql);
				
				// 绑定参数
				int index = 1;
				for (String key : keys) {
					Object value = values.get(key);
					if (value instanceof String) {
						statement.bindString(index, (String) value);
					} else if (value instanceof Integer) {
						statement.bindLong(index, (Integer) value);
					} else if (value instanceof Long) {
						statement.bindLong(index, (Long) value);
					} else if (value instanceof Double) {
						statement.bindDouble(index, (Double) value);
					} else if (value instanceof Float) {
						statement.bindDouble(index, (Float) value);
					} else if (value instanceof Boolean) {
						statement.bindLong(index, (Boolean) value ? 1 : 0);
					} else if (value == null) {
						statement.bindNull(index);
					}
					index++;
				}
				
				long result = statement.executeInsert();
				statement.close();
				
				if (result != -1) {
					log(LogLevel.INFO, TAG, "数据插入成功，行ID: " + result, null);
				}
				return result;
				
			} catch (SQLException e) {
				// 详细错误诊断
				String errorMsg = e.getMessage();
				log(LogLevel.ERROR, TAG, "SQL执行失败: " + errorMsg, e);
				
				// 智能错误分析
				if (errorMsg.contains("no such table")) {
					log(LogLevel.ERROR, TAG, "表不存在: " + tableName, null);
				} else if (errorMsg.contains("has no column")) {
					// 提取列名
					String columnName = extractColumnName(errorMsg);
					log(LogLevel.ERROR, TAG, "字段不存在: " + columnName, null);
				} else if (errorMsg.contains("NOT NULL constraint failed")) {
					String columnName = extractColumnName(errorMsg);
					log(LogLevel.ERROR, TAG, "违反非空约束: " + columnName, null);
				} else if (errorMsg.contains("UNIQUE constraint failed")) {
					String columnName = extractColumnName(errorMsg);
					log(LogLevel.ERROR, TAG, "违反唯一约束: " + columnName, null);
				} else if (errorMsg.contains("FOREIGN KEY constraint failed")) {
					log(LogLevel.ERROR, TAG, "违反外键约束", null);
				}
				
				return -1L;
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "插入操作异常: " + e.getMessage(), e);
				return -1L;
			}
		});
	}
	
	// 辅助方法：从错误消息中提取列名
	private String extractColumnName(String errorMsg) {
		// 尝试从错误消息中提取列名
		if (errorMsg.contains("has no column")) {
			return errorMsg.substring(errorMsg.indexOf("has no column") + 14);
		} else if (errorMsg.contains("NOT NULL constraint failed")) {
			return errorMsg.substring(errorMsg.indexOf(": ") + 2);
		} else if (errorMsg.contains("UNIQUE constraint failed")) {
			return errorMsg.substring(errorMsg.indexOf(": ") + 2);
		}
		return "未知字段";
	}
	
	
	
	/**
	* 使用JSON格式数据插入单条记录
	* @param tableName 表名
	* @param jsonString JSON格式的字符串
	* @return 插入的行ID，-1表示插入失败
	*/
	public long insertDataWithJson(String tableName, String jsonString) {
		ContentValues values = sqlUtilManager.jsonToContentValues(jsonString);
		if (values != null) {
			return insertData(tableName, values);
		} else {
			log(LogLevel.ERROR, TAG, "JSON格式数据插入失败：JSON转换ContentValues失败", null);
			return -1;
		}
	}
	
	/**
	* 使用JSONObject插入单条记录
	* @param tableName 表名
	* @param jsonObject JSONObject对象
	* @return 插入的行ID，-1表示插入失败
	*/
	public long insertDataWithJson(String tableName, JSONObject jsonObject) {
		ContentValues values =sqlUtilManager.jsonToContentValues(jsonObject);
		return insertData(tableName, values);
	}
	
	/**
	* 批量插入数据（支持多字段设置）
	* @param tableName 要插入数据的表名
	* @param dataList 包含多行数据的ContentValues列表
	* @return 成功插入的行数，-1表示插入失败
	*/
	public int batchInsertData(String tableName, List<ContentValues> dataList) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "开始批量插入数据，表名: " + tableName + ", 数据量: " + dataList.size(), null);
			
			if (dataList == null || dataList.isEmpty()) {
				log(LogLevel.WARN, TAG, "批量插入数据列表为空，跳过操作", null);
				return 0;
			}
			
			int successCount = 0;
			long startTime = System.currentTimeMillis();
			
			try {
				db.beginTransaction();
				
				for (int i = 0; i < dataList.size(); i++) {
					ContentValues values = dataList.get(i);
					
					if (values != null && values.size() > 0) {
						try {
							long result = db.insert(tableName, null, values);
							if (result != -1) {
								successCount++;
							} else {
								log(LogLevel.WARN, TAG, "第 " + (i + 1) + " 行数据插入失败", null);
							}
						} catch (SQLException e) {
							log(LogLevel.ERROR, TAG, "插入第 " + (i + 1) + " 行数据时发生SQL异常", e);
						}
					}
				}
				
				db.setTransactionSuccessful();
				long endTime = System.currentTimeMillis();
				log(LogLevel.INFO, TAG,
				String.format("批量插入完成，成功: %d/%d, 耗时: %dms", successCount, dataList.size(), (endTime - startTime)), null);
				
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "批量插入事务执行失败", e);
				successCount = -1;
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(LogLevel.WARN, TAG, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	/**
	* 使用JSON数组批量插入数据
	* @param tableName 表名
	* @param jsonArray JSONArray对象，每个元素是一条记录
	* @return 成功插入的行数，-1表示插入失败
	*/
	public int batchInsertDataWithJson(String tableName, JSONArray jsonArray) {
		log(LogLevel.DEBUG, TAG, "开始JSON批量插入数据，表名: " + tableName + ", 数据量: " + jsonArray.length(), null);
		
		List<ContentValues> dataList = new ArrayList<>();
		try {
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				ContentValues values = sqlUtilManager.jsonToContentValues(jsonObject);
				if (values != null) {
					dataList.add(values);
				}
			}
			return batchInsertData(tableName, dataList);
		} catch (JSONException e) {
			log(LogLevel.ERROR, TAG, "JSON批量插入数据转换失败", e);
			return -1;
		}
	}
	
	// ==================== 数据操作 - 更新 ====================
	
	/**
	* 更新指定表中的数据（支持多字段更新）
	* @param tableName 要操作的表名
	* @param values 包含要更新字段和值的ContentValues对象
	* @param whereClause WHERE条件子句（如 "id = ?"）
	* @param whereArgs WHERE条件参数（如 new String[]{"1"}）
	* @return 受影响的行数，-1表示更新失败
	*/
	public int updateData(String tableName, ContentValues values, String whereClause, String[] whereArgs) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "开始更新数据，表名: " + tableName +
			", 条件: " + whereClause + ", 更新字段数: " + values.size(), null);
			
			if (values == null || values.size() == 0) {
				log(LogLevel.WARN, TAG, "更新数据失败：ContentValues为空", null);
				return 0;
			}
			
			int affectedRows = 0;
			
			try {
				affectedRows = db.update(tableName, values, whereClause, whereArgs);
				
				if (affectedRows > 0) {
					log(LogLevel.INFO, TAG, "数据更新成功，影响行: " + affectedRows, null);
				} else {
					log(LogLevel.WARN, TAG, "数据更新完成，但未影响任何行（可能条件不匹配）", null);
				}
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "更新数据时发生SQL异常", e);
				affectedRows = -1;
			}
			return affectedRows;
		});
	}
	
	/**
	* 使用JSON格式数据更新记录
	* @param tableName 表名
	* @param jsonString JSON格式的字符串
	* @param whereClause WHERE条件
	* @param whereArgs 条件参数
	* @return 受影响的行数
	*/
	public int updateDataWithJson(String tableName, String jsonString, String whereClause, String[] whereArgs) {
		ContentValues values = sqlUtilManager.jsonToContentValues(jsonString);
		if (values != null) {
			return updateData(tableName, values, whereClause, whereArgs);
		} else {
			log(LogLevel.ERROR, TAG, "JSON格式数据更新失败：JSON转换ContentValues失败", null);
			return -1;
		}
	}
	
	/**
	* 根据ID更新单条数据
	* @param tableName 表名
	* @param id 要更新的记录ID
	* @param values 包含更新字段和值的ContentValues
	* @return 受影响的行数
	*/
	public int updateDataById(String tableName, long id, ContentValues values) {
		String whereClause = "id = ?";
		String[] whereArgs = new String[]{String.valueOf(id)};
		return updateData(tableName, values, whereClause, whereArgs);
	}
	
	/**
	* 使用JSON格式数据根据ID更新记录
	* @param tableName 表名
	* @param id 记录ID
	* @param jsonString JSON数据
	* @return 受影响的行数
	*/
	public int updateDataByIdWithJson(String tableName, long id, String jsonString) {
		ContentValues values = sqlUtilManager.jsonToContentValues(jsonString);
		if (values != null) {
			return updateDataById(tableName, id, values);
		} else {
			log(LogLevel.ERROR, TAG, "JSON格式数据更新失败：JSON转换ContentValues失败", null);
			return -1;
		}
	}
	
	/**
	* 批量更新数据
	* @param tableName 表名
	* @param valuesList 包含多行数据的ContentValues列表
	* @param whereClause WHERE条件模板（使用?占位符）
	* @param whereArgsList WHERE条件参数列表（每个元素对应一行的参数数组）
	* @return 成功更新的行数，-1表示更新失败
	*/
	public int batchUpdateData(String tableName, List<ContentValues> valuesList,
	String whereClause, List<String[]> whereArgsList) {
		return executeWithConnection(db -> {
			if (valuesList == null || valuesList.isEmpty()) {
				log(LogLevel.WARN, TAG, "批量更新数据列表为空", null);
				return 0;
			}
			
			if (whereArgsList == null || whereArgsList.size() != valuesList.size()) {
				log(LogLevel.ERROR, TAG, "批量更新失败：参数列表不匹配", null);
				return -1;
			}
			
			int successCount = 0;
			long startTime = System.currentTimeMillis();
			
			try {
				db.beginTransaction();
				
				for (int i = 0; i < valuesList.size(); i++) {
					ContentValues values = valuesList.get(i);
					String[] whereArgs = whereArgsList.get(i);
					
					try {
						int affected = db.update(tableName, values, whereClause, whereArgs);
						if (affected > 0) {
							successCount++;
						}
					} catch (SQLException e) {
						log(LogLevel.ERROR, TAG, "更新第 " + (i+1) + " 行数据失败", e);
					}
				}
				
				db.setTransactionSuccessful();
				long endTime = System.currentTimeMillis();
				log(LogLevel.INFO, TAG,
				String.format("批量更新完成，成功: %d/%d, 耗时: %dms",
				successCount, valuesList.size(), (endTime - startTime)), null);
				
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "批量更新事务执行失败", e);
				successCount = -1;
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(LogLevel.WARN, TAG, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	// ==================== 数据操作 - 删除 ====================
	
	/**
	* 删除指定表中符合条件的数据
	* @param tableName 要操作的表名
	* @param whereClause WHERE条件
	* @param whereArgs 条件参数
	* @return 受影响的行数
	*/
	public int deleteData(String tableName, String whereClause, String[] whereArgs) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "开始删除数据，表名: " + tableName + ", 条件: " + whereClause, null);
			
			int affectedRows = 0;
			
			try {
				affectedRows = db.delete(tableName, whereClause, whereArgs);
				log(LogLevel.INFO, TAG, "删除操作完成，影响行数: " + affectedRows, null);
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "删除数据时发生SQL异常", e);
				affectedRows = -1;
			}
			return affectedRows;
		});
	}
	
	/**
	* 批量删除数据
	* @param tableName 表名
	* @param whereClause WHERE条件模板（使用?占位符）
	* @param whereArgsList WHERE条件参数列表（每个元素对应一行的参数数组）
	* @return 成功删除的行数，-1表示删除失败
	*/
	public int batchDeleteData(String tableName, String whereClause, List<String[]> whereArgsList) {
		return executeWithConnection(db -> {
			if (whereArgsList == null || whereArgsList.isEmpty()) {
				log(LogLevel.WARN, TAG, "批量删除条件列表为空", null);
				return 0;
			}
			
			int successCount = 0;
			long startTime = System.currentTimeMillis();
			
			try {
				db.beginTransaction();
				
				for (int i = 0; i < whereArgsList.size(); i++) {
					String[] whereArgs = whereArgsList.get(i);
					
					try {
						int affected = db.delete(tableName, whereClause, whereArgs);
						if (affected > 0) {
							successCount++;
						}
					} catch (SQLException e) {
						log(LogLevel.ERROR, TAG, "删除第 " + (i+1) + " 行数据失败", e);
					}
				}
				
				db.setTransactionSuccessful();
				long endTime = System.currentTimeMillis();
				log(LogLevel.INFO, TAG,
				String.format("批量删除完成，成功: %d/%d, 耗时: %dms",
				successCount, whereArgsList.size(), (endTime - startTime)), null);
				
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "批量删除事务执行失败", e);
				successCount = -1;
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(LogLevel.WARN, TAG, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	/**
	* 根据ID删除单条记录
	* @param tableName 表名
	* @param id 要删除的记录ID
	* @return 受影响的行数
	*/
	public int deleteDataById(String tableName, long id) {
		return deleteData(tableName, "id = ?", new String[]{String.valueOf(id)});
	}
	
	/**
	* 根据多个ID批量删除记录
	* @param tableName 表名
	* @param ids 要删除的ID列表
	* @return 成功删除的行数
	*/
	public int deleteDataByIds(String tableName, List<Long> ids) {
		return executeWithConnection(db -> {
			if (ids == null || ids.isEmpty()) {
				log(LogLevel.WARN, TAG, "删除ID列表为空，跳过操作", null);
				return 0;
			}
			
			int successCount = 0;
			long startTime = System.currentTimeMillis();
			
			try {
				db.beginTransaction();
				
				for (Long id : ids) {
					try {
						int affected = db.delete(tableName, "id = ?", new String[]{String.valueOf(id)});
						if (affected > 0) {
							successCount++;
						}
					} catch (SQLException e) {
						log(LogLevel.ERROR, TAG, "删除ID为 " + id + " 的记录失败", e);
					}
				}
				
				db.setTransactionSuccessful();
				long endTime = System.currentTimeMillis();
				log(LogLevel.INFO, TAG,
				String.format("批量ID删除完成，成功: %d/%d, 耗时: %dms",
				successCount, ids.size(), (endTime - startTime)), null);
				
			} catch (Exception e) {
				log(LogLevel.ERROR, TAG, "批量ID删除事务执行失败", e);
				successCount = -1;
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(LogLevel.WARN, TAG, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	/**
	* 使用IN条件批量删除（更高效的方式）
	* @param tableName 表名
	* @param idColumn ID列名（如"角色ID"）
	* @param ids 要删除的ID列表
	* @return 受影响的行数
	*/
	public int deleteDataWithInClause(String tableName, String idColumn, List<Long> ids) {
		return executeWithConnection(db -> {
			if (ids == null || ids.isEmpty()) {
				return 0;
			}
			
			// 构建IN条件的占位符
			StringBuilder placeholders = new StringBuilder();
			String[] whereArgs = new String[ids.size()];
			for (int i = 0; i < ids.size(); i++) {
				placeholders.append("?");
				if (i < ids.size() - 1) {
					placeholders.append(",");
				}
				whereArgs[i] = String.valueOf(ids.get(i));
			}
			
			String whereClause = idColumn + " IN (" + placeholders.toString() + ")";
			
			try {
				int affectedRows = db.delete(tableName, whereClause, whereArgs);
				log(LogLevel.INFO, TAG, "IN条件删除完成，影响行数: " + affectedRows, null);
				return affectedRows;
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "IN条件删除失败", e);
				return -1;
			}
		});
	}
	
	
	// ==================== 数据操作 - 查询 ====================
	
	/**
	* 查询表中的所有数据
	* @param tableName 表名
	* @return 包含所有记录的ContentValues列表
	*/
	public List<ContentValues> queryAll(String tableName) {
		return query(tableName, null, null, null, null, null, null);
	}
	
	/**
	* 带条件的查询
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @return 符合条件的ContentValues列表
	*/
	public List<ContentValues> query(String tableName, String selection, String[] selectionArgs) {
		return query(tableName, null, selection, selectionArgs, null, null, null);
	}
	
	/**
	* 带条件和排序的查询
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @param orderBy 排序字段
	* @return 符合条件的ContentValues列表
	*/
	public List<ContentValues> query(String tableName, String selection, String[] selectionArgs, String orderBy) {
		return query(tableName, null, selection, selectionArgs, null, null, orderBy);
	}
	
	/**
	* 带条件、排序和分页的查询
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @param orderBy 排序字段
	* @param limit 返回记录数限制
	* @return 符合条件的ContentValues列表
	*/
	public List<ContentValues> query(String tableName, String selection, String[] selectionArgs, String orderBy, String limit) {
		return query(tableName, null, selection, selectionArgs, null, null, orderBy, limit);
	}
	
	/**
	* 完整的查询方法
	* @param tableName 表名
	* @param columns 要查询的列（null表示所有列）
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @param groupBy GROUP BY子句
	* @param having HAVING子句
	* @param orderBy 排序字段
	* @return 符合条件的ContentValues列表
	*/
	public List<ContentValues> query(String tableName, String[] columns, String selection,
	String[] selectionArgs, String groupBy, String having,
	String orderBy) {
		return query(tableName, columns, selection, selectionArgs, groupBy, having, orderBy, null);
	}
	
	/**
	* 完整的查询方法（带分页）
	* @param tableName 表名
	* @param columns 要查询的列（null表示所有列）
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @param groupBy GROUP BY子句
	* @param having HAVING子句
	* @param orderBy 排序字段
	* @param limit 返回记录数限制
	* @return 符合条件的ContentValues列表
	*/
	public List<ContentValues> query(String tableName, String[] columns, String selection,
	String[] selectionArgs, String groupBy, String having,
	String orderBy, String limit) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "执行查询: " + tableName +
			(selection != null ? " WHERE " + selection : ""), null);
			
			List<ContentValues> resultList = new ArrayList<>();
			Cursor cursor = null;
			
			try {
				cursor = db.query(tableName, columns, selection, selectionArgs,
				groupBy, having, orderBy, limit);
				
				if (cursor != null) {
					while (cursor.moveToNext()) {
						ContentValues values = new ContentValues();
						int columnCount = cursor.getColumnCount();
						
						for (int i = 0; i < columnCount; i++) {
							String columnName = cursor.getColumnName(i);
							int columnType = cursor.getType(i);
							
							switch (columnType) {
								case Cursor.FIELD_TYPE_NULL:
								values.putNull(columnName);
								break;
								case Cursor.FIELD_TYPE_INTEGER:
								values.put(columnName, cursor.getLong(i));
								break;
								case Cursor.FIELD_TYPE_FLOAT:
								values.put(columnName, cursor.getDouble(i));
								break;
								case Cursor.FIELD_TYPE_STRING:
								values.put(columnName, cursor.getString(i));
								break;
								case Cursor.FIELD_TYPE_BLOB:
								values.put(columnName, cursor.getBlob(i));
								break;
								default:
								values.put(columnName, cursor.getString(i));
								break;
							}
						}
						resultList.add(values);
					}
				}
				
				log(LogLevel.INFO, TAG, "查询完成，返回记录数: " + resultList.size(), null);
				return resultList;
				
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "查询数据时发生SQL异常", e);
				return new ArrayList<>();
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		});
	}
	
	/**
	* 查询单条记录
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @return 单条记录的ContentValues，如果没有结果返回null
	*/
	public ContentValues querySingle(String tableName, String selection, String[] selectionArgs) {
		List<ContentValues> results = query(tableName, null, selection, selectionArgs, null, null, null, "1");
		return results.isEmpty() ? null : results.get(0);
	}
	
	/**
	* 查询单条记录并返回json格式
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @return 单条记录的ContentValues，如果没有结果返回null
	*/
	public JSONObject querySingleTojson(String tableName, String selection, String[] selectionArgs) {
		return sqlUtilManager.contentValuesToJson(querySingle(tableName,selection,selectionArgs));
	}
	
	/**
	* 查询记录数量
	* @param tableName 表名
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @return 记录数量
	*/
	public long queryCount(String tableName, String selection, String[] selectionArgs) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "查询记录数量: " + tableName, null);
			
			Cursor cursor = null;
			long count = 0;
			
			try {
				cursor = db.query(tableName, new String[]{"count(*)"}, selection, selectionArgs, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					count = cursor.getLong(0);
				}
				log(LogLevel.INFO, TAG, "表 '" + tableName + "' 记录数量: " + count, null);
				return count;
				
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "查询记录数量时发生SQL异常", e);
				return 0L;
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		});
	}
	
	/**
	* 执行原始SQL查询
	* @param sql SQL语句
	* @param selectionArgs 查询参数
	* @return 查询结果的ContentValues列表
	*/
	public List<ContentValues> rawQuery(String sql, String[] selectionArgs) {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "执行原始SQL查询: " + sql, null);
			List<ContentValues> resultList = new ArrayList<>();
			Cursor cursor = null;
			
			try {
				cursor = db.rawQuery(sql, selectionArgs);
				
				if (cursor != null) {
					while (cursor.moveToNext()) {
						ContentValues values = new ContentValues();
						int columnCount = cursor.getColumnCount();
						
						for (int i = 0; i < columnCount; i++) {
							String columnName = cursor.getColumnName(i);
							int columnType = cursor.getType(i);
							
							switch (columnType) {
								case Cursor.FIELD_TYPE_NULL:
								values.putNull(columnName);
								break;
								case Cursor.FIELD_TYPE_INTEGER:
								values.put(columnName, cursor.getLong(i));
								break;
								case Cursor.FIELD_TYPE_FLOAT:
								values.put(columnName, cursor.getDouble(i));
								break;
								case Cursor.FIELD_TYPE_STRING:
								values.put(columnName, cursor.getString(i));
								break;
								case Cursor.FIELD_TYPE_BLOB:
								values.put(columnName, cursor.getBlob(i));
								break;
								default:
								values.put(columnName, cursor.getString(i));
								break;
							}
						}
						resultList.add(values);
					}
				}
				
				log(LogLevel.INFO, TAG, "原始SQL查询完成，返回记录数: " + resultList.size(), null);
				return resultList;
				
			} catch (SQLException e) {
				log(LogLevel.ERROR, TAG, "执行原始SQL查询时发生异常", e);
				return new ArrayList<>();
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		});
	}
	
	/**
	* 分页查询
	* @param tableName 表名
	* @param columns 查询列
	* @param selection WHERE条件
	* @param selectionArgs WHERE条件参数
	* @param orderBy 排序字段
	* @param page 页码（从1开始）
	* @param pageSize 每页记录数
	* @return 当前页的数据列表
	*/
	public List<ContentValues> queryPaged(String tableName, String[] columns,
	String selection, String[] selectionArgs,
	String orderBy, int page, int pageSize) {
		return executeWithConnection(db -> {
			int offset = (page - 1) * pageSize;
			String limit = pageSize + " OFFSET " + offset;
			
			return query(tableName, columns, selection, selectionArgs,
			null, null, orderBy, limit);
		});
	}
	
	
	// ==================== 数据库维护 ====================
	
	/**
	* 执行数据库优化（VACUUM）
	*/
	public void optimizeDatabase() {
		executeWithConnection(db -> {
			db.execSQL("VACUUM");
			log(LogLevel.INFO, TAG, "数据库优化完成", null);
			return null;
		});
	}
	
	/**
	* 重建所有索引
	*/
	public void rebuildIndexes() {
		executeWithConnection(db -> {
			Cursor cursor = null;
			try {
				// 获取所有表
				cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
				while (cursor.moveToNext()) {
					String tableName = cursor.getString(0);
					
					// 重建表索引
					db.execSQL("REINDEX " + tableName);
				}
				log(LogLevel.INFO, TAG, "所有索引重建完成", null);
			} finally {
				if (cursor != null) cursor.close();
			}
			return null;
		});
	}
	
	/**
	* 检查数据库完整性
	* @return true数据库完整，false数据库损坏
	*/
	public boolean checkDatabaseIntegrity() {
		return executeWithConnection(db -> {
			Cursor cursor = null;
			try {
				cursor = db.rawQuery("PRAGMA integrity_check", null);
				if (cursor != null && cursor.moveToFirst()) {
					String result = cursor.getString(0);
					return "ok".equalsIgnoreCase(result);
				}
				return false;
			} finally {
				if (cursor != null) cursor.close();
			}
		});
	}
	
	/**
	* 获取数据库大小（字节）
	* @return 数据库文件大小
	*/
	public long getDatabaseSize() {
		return executeWithConnection(db -> {
			File dbFile = new File(db.getPath());
			return dbFile.length();
		});
	}
	
	/**
	* 获取表大小（字节）
	* @param tableName 表名
	* @return 表数据大小
	*/
	public long getTableSize(String tableName) {
		return executeWithConnection(db -> {
			Cursor cursor = null;
			try {
				cursor = db.rawQuery("SELECT SUM(pgsize) FROM dbstat WHERE name=?", new String[]{tableName});
				if (cursor != null && cursor.moveToFirst()) {
					return cursor.getLong(0);
				}
				return 0L;
			} finally {
				if (cursor != null) cursor.close();
			}
		});
	}
	
	// ==================== 异步操作 ====================
	
	/**
	* 异步执行数据库操作
	* @param operation 数据库操作
	* @param callback 回调接口
	*/
	public <T> void executeAsync(DatabaseOperation<T> operation, DatabaseCallback<T> callback) {
		new Thread(() -> {
			try {
				T result = executeWithConnection(operation);
				callback.onSuccess(result);
			} catch (Exception e) {
				callback.onError(e);
			}
		}).start();
	}
	
	public interface DatabaseCallback<T> {
		void onSuccess(T result);
		void onError(Exception e);
	}
	
	
	
	
	// ==================== 数据库连接测试 ====================
	
	/**
	* 数据库连接测试方法
	* @return true连接成功，false连接失败
	*/
	public boolean testConnection() {
		return executeWithConnection(db -> {
			log(LogLevel.DEBUG, TAG, "开始数据库连接测试", null);
			
			if (db != null && db.isOpen()) {
				log(LogLevel.INFO, TAG, "数据库连接测试成功", null);
				return true;
			}
			return false;
		});
	}
	
}