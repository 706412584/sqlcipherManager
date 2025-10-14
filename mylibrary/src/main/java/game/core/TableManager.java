package game.core;

import android.util.Log;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.database.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.ContentValues;
import java.io.File;
public class TableManager {
	private static final String TAG = "TableManager";
	private final DBCipherManager dbManager;
	
	public TableManager(DBCipherManager dbManager) {
		this.dbManager = dbManager;
	}
	
	// ==================== 表结构管理方法 ====================
	
	/**
	* 判断表中某列是否存在
	* @param tableName 表名
	* @param columnName 列名
	* @return true列存在，false列不存在
	*/
	public boolean isColumnExists(String tableName, String columnName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "检查列是否存在 - 表: " + tableName + ", 列: " + columnName, null);
			
			Cursor cursor = null;
			boolean result = false;
			
			try {
				cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
				
				if (cursor != null) {
					while (cursor.moveToNext()) {
						String existingColumn = cursor.getString(cursor.getColumnIndexOrThrow("name"));
						if (columnName.equals(existingColumn)) {
							result = true;
							break;
						}
					}
				}
				
				log(DBCipherManager.LogLevel.INFO, "列 '" + columnName + "' 在表 '" + tableName + "' 中存在性检查结果: " + result, null);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "检查列存在性时发生异常", e);
			} finally {
				if (cursor != null) cursor.close();
			}
			return result;
		});
	}
	
	/**
	* 检查表结构是否匹配
	*/
	private boolean checkTableStructure(String tableName, ContentValues values) {
		
		return dbManager.executeWithConnection(db ->
		{
			
			try {
				// 检查表是否存在
				if (!isTableExists(tableName)) {
					log(DBCipherManager.LogLevel.ERROR,  "表不存在: " + tableName, null);
					return false;
				}
				
				// 获取表的所有列
				List<String> columns = getTableColumns(tableName);
				
				// 检查所有字段是否存在于表中
				for (String key : values.keySet()) {
					if (!columns.contains(key)) {
						log(DBCipherManager.LogLevel.ERROR,  "字段 '" + key + "' 不存在于表 '" + tableName + "'", null);
						return false;
					}
				}
				
				return true;
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR,  "检查表结构失败: " + e.getMessage(), e);
				return false;
			}
			
		});
		
		
	}
	
	
	/**
	* 修改字段类型（通过创建新表并迁移数据的方式实现）
	* 注意：SQLite不支持直接修改字段类型，此方法通过重建表实现
	* @param tableName 表名
	* @param columnName 字段名
	* @param newColumnType 新的字段类型
	* @return true修改成功，false修改失败
	*/
	public boolean changeColumnType(String tableName, String columnName, String newColumnType) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "修改字段类型 - 表: " + tableName + ", 字段: " + columnName + ", 新类型: " + newColumnType, null);
			
			boolean success = false;
			
			try {
				db.beginTransaction();
				
				// 1. 获取原表结构
				List<Map<String, String>> tableStructure = getTableStructure(tableName);
				if (tableStructure.isEmpty()) {
					throw new SQLException("无法获取表结构: " + tableName);
				}
				
				// 2. 检查字段是否存在
				boolean columnFound = false;
				StringBuilder newTableSchema = new StringBuilder();
				for (Map<String, String> column : tableStructure) {
					String currentColumnName = column.get("name");
					String currentColumnType = column.get("type");
					
					if (currentColumnName.equals(columnName)) {
						// 修改目标字段类型
						newTableSchema.append(currentColumnName).append(" ").append(newColumnType);
						columnFound = true;
					} else {
						// 保持其他字段不变
						newTableSchema.append(currentColumnName).append(" ").append(currentColumnType);
					}
					
					// 添加主键信息
					if ("1".equals(column.get("pk"))) {
						newTableSchema.append(" PRIMARY KEY");
					}
					
					// 添加非空约束
					if ("1".equals(column.get("notnull"))) {
						newTableSchema.append(" NOT NULL");
					}
					
					// 添加默认值
					String defaultValue = column.get("dflt_value");
					if (defaultValue != null && !defaultValue.equals("null")) {
						newTableSchema.append(" DEFAULT ").append(defaultValue);
					}
					
					newTableSchema.append(", ");
				}
				
				if (!columnFound) {
					throw new SQLException("字段不存在: " + columnName);
				}
				
				// 移除最后的逗号和空格
				String schema = newTableSchema.substring(0, newTableSchema.length() - 2);
				
				// 3. 创建临时表并迁移数据
				String tempTableName = tableName + "_temp";
				String createTempTableSQL = "CREATE TABLE " + tempTableName + " (" + schema + ");";
				String copyDataSQL = "INSERT INTO " + tempTableName + " SELECT * FROM " + tableName + ";";
				String dropOldTableSQL = "DROP TABLE " + tableName+ ";";
				String renameTableSQL = "ALTER TABLE " + tempTableName + " RENAME TO " + tableName + ";";
				
				db.execSQL(createTempTableSQL);
				db.execSQL(copyDataSQL);
				db.execSQL(dropOldTableSQL);
				db.execSQL(renameTableSQL);
				db.setTransactionSuccessful();
				success = true;
				log(DBCipherManager.LogLevel.INFO, "字段类型修改成功", null);
				
			} catch (SQLException e) {
				log(DBCipherManager.LogLevel.ERROR, "修改字段类型失败", e);
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(DBCipherManager.LogLevel.WARN, "结束事务时发生异常", e);
				}
			}
			return success;
		});
	}
	
	/**
	* 获取表的第一列名
	* @param db 数据库连接
	* @param tableName 表名
	* @return 第一列名
	*/
	public String getFirstColumnName(SQLiteDatabase db, String tableName) {
		Cursor cursor = null;
		try {
			cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
			if (cursor != null && cursor.moveToFirst()) {
				return cursor.getString(cursor.getColumnIndexOrThrow("name"));
			}
			return "column1"; // 默认列名
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	* 批量为表添加多个字段（如果字段已存在则跳过）
	* @param tableName 目标表名
	* @param columnDefinitions 字段定义列表，每个元素是一个Map，包含name, type, defaultValue, comment等键
	* @return true表示所有字段添加成功或已存在，false表示有失败
	*/
	public boolean batchAddColumns(String tableName, List<Map<String, String>> columnDefinitions) {
		return dbManager.executeWithConnection(db -> {
			if (columnDefinitions == null || columnDefinitions.isEmpty()) {
				log(DBCipherManager.LogLevel.WARN, "批量添加字段失败：字段定义列表为空", null);
				return false;
			}
			
			boolean allSuccess = true;
			
			try {
				db.beginTransaction();
				
				StringBuilder sqlBuilder = new StringBuilder("ALTER TABLE ");
				sqlBuilder.append(tableName);
				
				for (int i = 0; i < columnDefinitions.size(); i++) {
					Map<String, String> column = columnDefinitions.get(i);
					String columnName = column.get("name");
					String columnType = column.get("type");
					String defaultValue = column.get("defaultValue");
					String comment = column.get("comment");
					
					// 检查字段是否已存在
					if (isColumnExists(tableName, columnName)) {
						log(DBCipherManager.LogLevel.DEBUG, "字段 '" + columnName + "' 已存在，跳过", null);
						continue;
					}
					
					// 拼接单个字段的SQL，例如：ADD COLUMN username TEXT DEFAULT 'unknown' COMMENT '用户名'
					sqlBuilder.append(" ADD COLUMN ").append(columnName).append(" ").append(columnType);
					
					if (defaultValue != null && !defaultValue.isEmpty()) {
						sqlBuilder.append(" DEFAULT ").append(defaultValue);
					}
					if (comment != null && !comment.isEmpty()) {
						sqlBuilder.append(" COMMENT '").append(comment).append("'");
					}
					
					if (i < columnDefinitions.size() - 1) {
						sqlBuilder.append(","); // 最后一个字段后不加逗号
					}
				}
				
				// 检查是否生成了有效的SQL（有可能所有字段都已存在）
				String finalSql = sqlBuilder.toString();
				if (!finalSql.equals("ALTER TABLE " + tableName)) {
					db.execSQL(finalSql);
					log(DBCipherManager.LogLevel.INFO, "执行批量添加字段SQL: " + finalSql, null);
				} else {
					log(DBCipherManager.LogLevel.INFO, "所有待添加字段均已存在，无需操作", null);
				}
				
				db.setTransactionSuccessful();
				log(DBCipherManager.LogLevel.INFO, "批量添加字段操作成功完成", null);
				
			} catch (SQLException e) {
				allSuccess = false;
				log(DBCipherManager.LogLevel.ERROR, "批量添加字段时发生SQL异常", e);
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(DBCipherManager.LogLevel.WARN, "结束事务时发生异常", e);
				}
			}
			return allSuccess;
		});
	}
	
	/**
	* 为表添加新字段（如果字段不存在）
	* @param tableName 表名
	* @param columnName 字段名
	* @param columnType 字段类型（如 TEXT, INTEGER, REAL等）
	* @return true添加成功或字段已存在，false添加失败
	*/
	public boolean addColumnIfNotExists(String tableName, String columnName, String columnType) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "为表 '" + tableName + "' 添加字段: " + columnName + " " + columnType, null);
			
			// 先检查字段是否存在
			if (isColumnExists(tableName, columnName)) {
				log(DBCipherManager.LogLevel.INFO, "字段 '" + columnName + "' 在表 '" + tableName + "' 中已存在，跳过添加", null);
				return true;
			}
			
			boolean success = false;
			
			try {
				String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";";
				db.execSQL(sql);
				success = true;
				log(DBCipherManager.LogLevel.INFO, "字段 '" + columnName + "' 添加成功", null);
			} catch (SQLException e) {
				log(DBCipherManager.LogLevel.ERROR, "添加字段失败: " + columnName, e);
			}
			return success;
		});
	}
	
	/**
	* 创建表（如果不存在则创建，存在则跳过）
	* @param tableName 表名
	* @param tableSchema 表结构SQL（不包含CREATE TABLE部分）
	* @return true创建成功或表已存在，false创建失败
	*/
	public boolean createTableIfNotExists(String tableName, String tableSchema) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "创建表（如果不存在）: " + tableName, null);
			
			// 先检查表是否存在
			if (isTableExists(tableName)) {
				log(DBCipherManager.LogLevel.INFO, "表 '" + tableName + "' 已存在，跳过创建", null);
				return true;
			}
			
			boolean success = false;
			
			try {
				String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + tableSchema + ");";
				db.execSQL(sql);
				success = true;
				log(DBCipherManager.LogLevel.INFO, "表 '" + tableName + "' 创建成功", null);
			} catch (SQLException e) {
				log(DBCipherManager.LogLevel.ERROR, "创建表失败: " + tableName,e);
			}
			return success;
		});
	}
	
	/**
	* 判断表是否存在
	* @param tableName 要检查的表名
	* @return true表存在，false表不存在
	*/
	public boolean isTableExists(String tableName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "检查表是否存在: " + tableName, null);
			
			Cursor cursor = null;
			boolean result = false;
			
			try {
				String sql = "SELECT count(*) as c FROM sqlite_master WHERE type='table' AND name=?";
				cursor = db.rawQuery(sql, new String[]{tableName});
				
				if (cursor != null && cursor.moveToFirst()) {
					int count = cursor.getInt(0);
					result = count > 0;
				}
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "检查表存在性时发生异常", e);
			} finally {
				if (cursor != null) cursor.close();
			}
			return result;
		});
	}
	
	/**
	* 获取数据库中所有用户表的表名
	* @return 表名列表（不包括系统表）
	*/
	public List<String> getAllTableNames() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "获取所有表名", null);
			
			if (db == null) {
				log(DBCipherManager.LogLevel.ERROR, "数据库连接无效，无法获取表名", null);
				return new ArrayList<>();
			}
			List<String> tableNames = new ArrayList<>();
			Cursor cursor = null;
			
			try {
				// 查询sqlite_master获取所有用户表
				String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name != 'sqlite_sequence' AND name != 'android_metadata'";
				cursor = db.rawQuery(sql, null);
				
				while (cursor != null && cursor.moveToNext()) {
					String tableName = cursor.getString(0);
					tableNames.add(tableName);
				}
				
				log(DBCipherManager.LogLevel.INFO, "共发现 " + tableNames.size() + " 个用户表", null);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "获取表名列表时发生异常", e);
			} finally {
				if (cursor != null) cursor.close();
			}
			return tableNames;
		});
	}
	
	/**
	* 获取表结构信息
	* @param tableName 表名
	* @return 列信息列表（列名、类型等）
	*/
	public List<Map<String, String>> getTableStructure(String tableName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "获取表结构: " + tableName, null);
			
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
				
				log(DBCipherManager.LogLevel.INFO, "表 '" + tableName + "' 共有 " + columns.size() + " 个列", null);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "获取表结构时发生异常", e);
			} finally {
				if (cursor != null) cursor.close();
			}
			return columns;
		});
	}
	
	/**
	* 获取整个数据库的表结构信息（JSON格式）
	* @return 包含所有表结构的JSONObject
	*/
	public JSONObject getAllTableStructureJson() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始导出数据库表结构为JSON", null);
			
			if (db == null) {
				log(DBCipherManager.LogLevel.ERROR, "数据库连接无效，无法导出结构", null);
				return new JSONObject();
			}
			JSONObject databaseStructure = new JSONObject();
			
			try {
				// 获取所有表名
				List<String> tableNames = getAllTableNames();
				
				// 添加数据库基本信息
				databaseStructure.put("database_name", dbManager.getDatabaseName());
				databaseStructure.put("table_count", tableNames.size());
				databaseStructure.put("export_time", System.currentTimeMillis());
				
				// 为每个表添加结构信息
				JSONObject tablesJson = new JSONObject();
				for (String tableName : tableNames) {
					List<Map<String, String>> tableStructure = getTableStructure(tableName);
					JSONArray columnsArray = new JSONArray();
					
					for (Map<String, String> column : tableStructure) {
						JSONObject columnJson = new JSONObject();
						columnJson.put("name", column.get("name"));
						columnJson.put("type", column.get("type"));
						columnJson.put("primary_key", "1".equals(column.get("pk")));
						columnJson.put("not_null", "1".equals(column.get("notnull")));
						columnJson.put("default_value", column.get("dflt_value"));
						columnsArray.put(columnJson);
					}
					
					tablesJson.put(tableName, columnsArray);
				}
				
				databaseStructure.put("tables", tablesJson);
				log(DBCipherManager.LogLevel.INFO, "数据库表结构导出完成", null);
				
			} catch (JSONException e) {
				log(DBCipherManager.LogLevel.ERROR, "构建JSON结构时发生异常", e);
			}
			return databaseStructure;
		});
	}
	
	
	/**
	* 获取表的所有列名
	*/
	private List<String> getTableColumns(String tableName) {
		
		return dbManager.executeWithConnection(db -> {
			List<String> columns = new ArrayList<>();
			Cursor cursor = null;
			try {
				cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
				while (cursor != null && cursor.moveToNext()) {
					columns.add(cursor.getString(cursor.getColumnIndex("name")));
				}
				return columns;
			} finally {
				if (cursor != null) cursor.close();
			}
			
		});
	}
	
	
	// ==================== 表数据删除方法 ====================
	
	/**
	* 清空指定表的所有数据（保留表结构）
	* @param tableName 要清空的表名
	* @return true操作成功，false操作失败
	*/
	public boolean truncateTable(String tableName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "开始清空表数据: " + tableName, null);
			
			try {
				// 使用DELETE语句清空表
				db.execSQL("DELETE FROM " + tableName);
				
				// 重置自增ID计数器
				db.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", new String[]{tableName});
				
				log(DBCipherManager.LogLevel.INFO, "表数据清空成功: " + tableName, null);
				return true;
			} catch (SQLException e) {
				log(DBCipherManager.LogLevel.ERROR, "清空表数据失败: " + tableName, e);
				return false;
			}
		});
	}
	
	/**
	* 删除指定表（不保留表结构）
	* @param tableName 要删除的表名
	* @return true删除成功，false删除失败
	*/
	public boolean dropTable(String tableName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "开始删除表: " + tableName, null);
			
			try {
				// 执行DROP TABLE语句
				db.execSQL("DROP TABLE IF EXISTS " + tableName);
				log(DBCipherManager.LogLevel.INFO, "表删除成功: " + tableName, null);
				return true;
			} catch (SQLException e) {
				log(DBCipherManager.LogLevel.ERROR, "删除表失败: " + tableName, e);
				return false;
			}
		});
	}
	
	/**
	* 清空数据库中所有表的数据（保留表结构）
	* @return 成功清空的表数量
	*/
	public int truncateAllTables() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "开始清空所有表数据", null);
			
			List<String> tableNames = getAllTableNames();
			int successCount = 0;
			
			try {
				db.beginTransaction();
				
				for (String tableName : tableNames) {
					if (truncateTable(tableName)) {
						successCount++;
					}
				}
				
				db.setTransactionSuccessful();
				log(DBCipherManager.LogLevel.INFO,
				"所有表数据清空完成，成功: " + successCount + "/" + tableNames.size(), null);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "清空所有表数据时发生异常", e);
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(DBCipherManager.LogLevel.WARN, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	/**
	* 删除数据库中所有表（不保留表结构）
	* @return 成功删除的表数量
	*/
	public int dropAllTables() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "开始删除所有表", null);
			
			List<String> tableNames = getAllTableNames();
			int successCount = 0;
			
			try {
				db.beginTransaction();
				
				for (String tableName : tableNames) {
					if (dropTable(tableName)) {
						successCount++;
					}
				}
				
				db.setTransactionSuccessful();
				log(DBCipherManager.LogLevel.INFO,
				"所有表删除完成，成功: " + successCount + "/" + tableNames.size(), null);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "删除所有表时发生异常", e);
			} finally {
				try {
					db.endTransaction();
				} catch (Exception e) {
					log(DBCipherManager.LogLevel.WARN, "结束事务时发生异常", e);
				}
			}
			return successCount;
		});
	}
	
	/**
	* 删除整个数据库（包括所有表和数据）
	* @return true删除成功，false删除失败
	*/
	public boolean deleteDatabase() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "开始删除整个数据库", null);
			
			try {
				// 关闭数据库连接
				db.close();
				
				// 获取数据库文件路径
				String dbPath = db.getPath();
				
				// 删除主数据库文件
				File dbFile = new File(dbPath);
				boolean mainDeleted = dbFile.delete();
				
				// 删除辅助文件
				boolean walDeleted = new File(dbPath + "-wal").delete();
				boolean shmDeleted = new File(dbPath + "-shm").delete();
				boolean journalDeleted = new File(dbPath + "-journal").delete();
				
				if (mainDeleted || walDeleted || shmDeleted || journalDeleted) {
					log(DBCipherManager.LogLevel.INFO, "数据库文件删除成功", null);
					return true;
				} else {
					log(DBCipherManager.LogLevel.ERROR, "数据库文件删除失败", null);
					return false;
				}
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "删除数据库时发生异常", e);
				return false;
			}
		});
	}
	
	
	
	// ==================== 日志辅助方法 ====================
	
	private void log(DBCipherManager.LogLevel level, String message, Throwable throwable) {
		this.dbManager.log(level, TAG, message, throwable);
	}
}