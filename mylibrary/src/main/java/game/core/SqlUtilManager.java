package game.core;

import android.content.ContentValues;
import android.util.Base64;
import android.util.Log;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Iterator;

/**
* SQL工具管理器 - 提供JSON转换和数据导入导出功能
* 与DBCipherManager配合使用，处理ContentValues与JSON之间的转换
*/
public class SqlUtilManager {
	private static final String TAG = "SqlUtilManager";
	private final DBCipherManager dbManager;
	private final TableManager mTableManager;
	public SqlUtilManager(DBCipherManager dbManager) {
		this.dbManager = dbManager;
        this.mTableManager=dbManager.getTableManager();
	}
	
	// ==================== 密钥派生方法 ====================
	
	/**
	* 使用PBKDF2算法从用户凭证派生加密密钥
	*/
	public static byte[] deriveEncryptionKey(String username, String clientSecret, String serverToken) {
		try {
			String saltSource = username + serverToken;
			byte[] salt = generateSalt(saltSource);
			
			char[] passwordChars = clientSecret.toCharArray();
			PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, 100000, 256);
			
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return factory.generateSecret(spec).getEncoded();
			
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException("密钥派生失败", e);
		}
	}
	
	private static byte[] generateSalt(String saltSource) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(saltSource.getBytes());
			byte[] salt = new byte[16];
			System.arraycopy(hash, 0, salt, 0, 16);
			return salt;
		} catch (NoSuchAlgorithmException e) {
			byte[] salt = new byte[16];
			new SecureRandom().nextBytes(salt);
			return salt;
		}
	}
	
	// ==================== JSON与ContentValues转换方法 ====================
	
	public static JSONObject contentValuesToJson(ContentValues values) {
		JSONObject jsonObject = new JSONObject();
		if (values == null) return jsonObject;
		
		try {
			for (Map.Entry<String, Object> entry : values.valueSet()) {
				String key = entry.getKey();
				Object value = entry.getValue();
				jsonObject.put(key, value);
			}
		} catch (JSONException e) {
			Log.e(TAG, "ContentValues转换JSON失败: " + e.getMessage());
		}
		return jsonObject;
	}
	
	public static ContentValues jsonToContentValues(JSONObject jsonObject) {
		ContentValues values = new ContentValues();
		if (jsonObject == null) return values;
		
		try {
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				Object value = jsonObject.get(key);
				
				if (value instanceof String) {
					values.put(key, (String) value);
				} else if (value instanceof Integer) {
					values.put(key, (Integer) value);
				} else if (value instanceof Long) {
					values.put(key, (Long) value);
				} else if (value instanceof Double) {
					values.put(key, (Double) value);
				} else if (value instanceof Float) {
					values.put(key, (Float) value);
				} else if (value instanceof Boolean) {
					values.put(key, (Boolean) value);
				} else if (value instanceof Byte) {
					values.put(key, (Byte) value);
				} else if (value instanceof byte[]) {
					values.put(key, (byte[]) value);
				} else if (value == JSONObject.NULL) {
					values.putNull(key);
				} else {
					values.put(key, value.toString());
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONObject转换ContentValues失败: " + e.getMessage());
		}
		return values;
	}
	
	public static ContentValues jsonToContentValues(String jsonString) {
		try {
			JSONObject jsonObject = new JSONObject(jsonString);
			return jsonToContentValues(jsonObject);
		} catch (JSONException e) {
			Log.e(TAG, "JSON字符串转换失败: " + e.getMessage());
			return null;
		}
	}
	
	// ==================== 数据导出功能 ====================
	
	/**
	* 将整个数据库导出为JSON格式（表名作为主键）
	* 格式：{表名: {字段1: 数据, ...}} 或 {表名: [{字段1: 数据, ...}, ...]}
	* @return 包含所有表数据和元信息的JSONObject
	*/
	public JSONObject exportDatabaseToJson() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始导出整个数据库为JSON格式", null);
			
			JSONObject databaseJson = new JSONObject();
			
			try {
				if (db == null || !db.isOpen()) {
					log(DBCipherManager.LogLevel.ERROR, "数据库连接未成功建立", null);
					return databaseJson;
				}
				
				List<String> tableNames = getAllTableNames(db);
				
				try {
					databaseJson.put("database_name", dbManager.getDatabaseName());
					databaseJson.put("export_time", System.currentTimeMillis());
					databaseJson.put("table_count", tableNames.size());
				} catch (JSONException e) {
					log(DBCipherManager.LogLevel.WARN, "构建数据库元信息时发生JSON异常", e);
				}
				
				JSONObject tablesJson = new JSONObject();
				for (String tableName : tableNames) {
					log(DBCipherManager.LogLevel.DEBUG, "正在导出表: " + tableName, null);
					
					// 获取表结构信息
					List<Map<String, String>> tableStructure = mTableManager.getTableStructure(tableName);
					int columnCount = tableStructure.size();
					
					// 导出表数据
					Object tableData = exportTableToJsonWithConnection(db, tableName, columnCount);
					
					try {
						tablesJson.put(tableName, tableData);
					} catch (JSONException e) {
						log(DBCipherManager.LogLevel.WARN, "添加表数据到JSON时发生异常: " + tableName, e);
					}
				}
				
				try {
					databaseJson.put("tables", tablesJson);
				} catch (JSONException e) {
					log(DBCipherManager.LogLevel.ERROR, "添加tables字段到数据库JSON时发生异常", e);
				}
				
				log(DBCipherManager.LogLevel.INFO, "数据库导出完成，共导出 " + tableNames.size() + " 个表", null);
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "导出整个数据库为JSON时发生异常", e);
			}
			return databaseJson;
		});
	}
	
	/**
	* 获取数据库中所有用户表的表名（排除系统表）
	*/
	private List<String> getAllTableNames(SQLiteDatabase db) {
		List<String> tableNames = new ArrayList<>();
		Cursor cursor = null;
		
		try {
			String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'";
			cursor = db.rawQuery(sql, null);
			
			while (cursor != null && cursor.moveToNext()) {
				String tableName = cursor.getString(0);
				tableNames.add(tableName);
				log(DBCipherManager.LogLevel.DEBUG, "发现用户表: " + tableName, null);
			}
			
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "获取表名列表时发生异常", e);
		} finally {
			if (cursor != null) cursor.close();
		}
		return tableNames;
	}
	
	/**
	* 导出指定表的数据
	* @param tableName 表名
	* @return 表数据对象（单行或多行）
	*/
	public Object exportTableToJson(String tableName) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.DEBUG, "导出表为JSON: " + tableName, null);
			
			try {
				// 获取表结构信息
				List<Map<String, String>> tableStructure = mTableManager.getTableStructure(tableName);
				int columnCount = tableStructure.size();
				
				return exportTableToJsonWithConnection(db, tableName, columnCount);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "导出表为JSON时发生异常", e);
				return null;
			}
		});
	}
	
	/**
	* 使用已打开的数据库连接导出单个表的数据
	* @param db 数据库连接
	* @param tableName 表名
	* @param columnCount 列数
	* @return 表数据对象（单行或多行）
	*/
	private Object exportTableToJsonWithConnection(SQLiteDatabase db, String tableName, int columnCount) {
		Cursor cursor = null;
		Object result = null;
		
		try {
			cursor = db.query(tableName, null, null, null, null, null, null);
			
			if (cursor != null) {
				int rowCount = cursor.getCount();
				String[] columnNames = cursor.getColumnNames();
				
				// 单行单列：直接返回单个值
				if (rowCount == 1 && columnCount == 1) {
					cursor.moveToFirst();
					return getValueFromCursor(cursor, 0);
				}
				// 单行多列：返回JSONObject
				else if (rowCount == 1 && columnCount > 1) {
					cursor.moveToFirst();
					JSONObject rowObject = new JSONObject();
					for (int i = 0; i < columnNames.length; i++) {
						rowObject.put(columnNames[i], getValueFromCursor(cursor, i));
					}
					return rowObject;
				}
				// 多行：返回JSONArray
				else {
					JSONArray resultArray = new JSONArray();
					while (cursor.moveToNext()) {
						// 单列：直接添加值
						if (columnCount == 1) {
							resultArray.put(getValueFromCursor(cursor, 0));
						}
						// 多列：添加JSONObject
						else {
							JSONObject rowObject = new JSONObject();
							for (int i = 0; i < columnNames.length; i++) {
								rowObject.put(columnNames[i], getValueFromCursor(cursor, i));
							}
							resultArray.put(rowObject);
						}
					}
					return resultArray;
				}
			}
			
			log(DBCipherManager.LogLevel.DEBUG, "表 '" + tableName + "' 数据导出完成", null);
			
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "导出表 '" + tableName + "' 为JSON时发生异常", e);
		} finally {
			if (cursor != null) cursor.close();
		}
		return result;
	}
	
	/**
	* 从游标获取值
	* @param cursor 数据库游标
	* @param columnIndex 列索引
	* @return 列值
	*/
	private Object getValueFromCursor(Cursor cursor, int columnIndex) {
		int columnType = cursor.getType(columnIndex);
		
		switch (columnType) {
			case Cursor.FIELD_TYPE_NULL:
			return JSONObject.NULL;
			case Cursor.FIELD_TYPE_INTEGER:
			return cursor.getLong(columnIndex);
			case Cursor.FIELD_TYPE_FLOAT:
			return cursor.getDouble(columnIndex);
			case Cursor.FIELD_TYPE_STRING:
			return cursor.getString(columnIndex);
			case Cursor.FIELD_TYPE_BLOB:
			byte[] blobData = cursor.getBlob(columnIndex);
			return Base64.encodeToString(blobData, Base64.DEFAULT);
			default:
			return cursor.getString(columnIndex);
		}
	}
	
	// ==================== 数据导入功能 ====================
	
	/**
	* 从JSON格式导入整个数据库
	* @param jsonData JSON格式的数据库数据
	* @param clearBeforeImport 是否在导入前清空表
	* @return 成功导入的表数量
	*/
	public int importDatabaseFromJson(JSONObject jsonData, boolean clearBeforeImport) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始导入数据库数据", null);
			
			int importedTables = 0;
			
			try {
				if (jsonData == null || !jsonData.has("tables")) {
					log(DBCipherManager.LogLevel.ERROR, "JSON数据无效或缺少tables字段", null);
					return 0;
				}
				
				JSONObject tablesJson = jsonData.getJSONObject("tables");
				Iterator<String> tableNames = tablesJson.keys();
				
				while (tableNames.hasNext()) {
					String tableName = tableNames.next();
					Object tableData = tablesJson.get(tableName);
					
					if (importTableData(db, tableName, tableData, clearBeforeImport)) {
						importedTables++;
					}
				}
				
				log(DBCipherManager.LogLevel.INFO, "数据库导入完成，成功导入 " + importedTables + " 个表", null);
			} catch (JSONException e) {
				log(DBCipherManager.LogLevel.ERROR, "解析JSON数据失败", e);
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "导入数据库时发生异常", e);
			}
			return importedTables;
		});
	}
	
	/**
	* 导入单个表的数据
	* @param db 数据库连接
	* @param tableName 表名
	* @param tableData 表数据（单值、JSONObject或JSONArray）
	* @param clearBeforeImport 是否在导入前清空表
	* @return true导入成功，false导入失败
	*/
	private boolean importTableData(SQLiteDatabase db, String tableName, Object tableData, boolean clearBeforeImport) {
		try {
			// 在导入前清空表
			if (clearBeforeImport) {
				db.execSQL("DELETE FROM " + tableName);
				log(DBCipherManager.LogLevel.DEBUG, "已清空表: " + tableName, null);
			}
			
			// 处理不同类型的数据
			if (tableData instanceof JSONObject) {
				// 单行多列数据
				ContentValues values = jsonToContentValues((JSONObject) tableData);
				long result = db.insert(tableName, null, values);
				return result != -1;
			}
			else if (tableData instanceof JSONArray) {
				// 多行数据
				JSONArray dataArray = (JSONArray) tableData;
				boolean allSuccess = true;
				
				db.beginTransaction();
				try {
					for (int i = 0; i < dataArray.length(); i++) {
						Object rowData = dataArray.get(i);
						
						if (rowData instanceof JSONObject) {
							// 多列数据行
							ContentValues values = jsonToContentValues((JSONObject) rowData);
							long result = db.insert(tableName, null, values);
							if (result == -1) allSuccess = false;
						} else {
							// 单列数据行
							ContentValues values = new ContentValues();
							values.put(mTableManager.getFirstColumnName(db, tableName), rowData.toString());
							long result = db.insert(tableName, null, values);
							if (result == -1) allSuccess = false;
						}
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
				return allSuccess;
			}
			else {
				// 单行单列数据
				ContentValues values = new ContentValues();
				values.put(mTableManager.getFirstColumnName(db, tableName), tableData.toString());
				long result = db.insert(tableName, null, values);
				return result != -1;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "导入表 '" + tableName + "' 数据失败", e);
			return false;
		}
	}
	
	
	// ==================== 日志辅助方法 ====================
	
	private void log(DBCipherManager.LogLevel level, String message, Throwable throwable) {
		this.dbManager.log(level, TAG, message, throwable);
	}
}