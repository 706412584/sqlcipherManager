package game.core;

import android.content.ContentValues;
import android.util.Base64;
import android.util.Log;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
	
	// ==================== 流式导出功能 ====================
	
	/**
	* 流式导出整个数据库到JSON格式
	* @param writer 输出写入器
	* @param progressListener 进度监听器（可选）
	* @return 是否成功导出
	*/
	public boolean exportDatabaseToJsonStream(Writer writer, ExportProgressListener progressListener) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始流式导出整个数据库为JSON格式", null);
			
			try {
				if (db == null || !db.isOpen()) {
					log(DBCipherManager.LogLevel.ERROR, "数据库连接未成功建立", null);
					return false;
				}
				
				List<String> tableNames = getAllTableNames(db);
				int totalTables = tableNames.size();
				
				// 开始写入JSON对象
				writer.write("{");
				
				// 写入元数据
				writer.write("\"database_name\":\"" + escapeJsonString(dbManager.getDatabaseName()) + "\",");
				writer.write("\"export_time\":" + System.currentTimeMillis() + ",");
				writer.write("\"table_count\":" + totalTables + ",");
				writer.write("\"tables\":{");
				
				// 导出每个表
				int tableIndex = 0;
				for (String tableName : tableNames) {
					if (progressListener != null) {
						progressListener.onTableStart(tableName, tableIndex, totalTables);
					}
					
					log(DBCipherManager.LogLevel.DEBUG, "正在流式导出表: " + tableName, null);
					
					// 写入表名
					writer.write("\"" + escapeJsonString(tableName) + "\":");
					
					// 导出表数据
					boolean success = exportTableToJsonStream(db, tableName, writer, progressListener);
					
					if (!success) {
						log(DBCipherManager.LogLevel.ERROR, "导出表失败: " + tableName, null);
						return false;
					}
					
					// 如果不是最后一个表，添加逗号
					if (tableIndex < totalTables - 1) {
						writer.write(",");
					}
					
					if (progressListener != null) {
						progressListener.onTableComplete(tableName, tableIndex, totalTables);
					}
					
					tableIndex++;
				}
				
				// 结束JSON对象
				writer.write("}}");
				writer.flush();
				
				log(DBCipherManager.LogLevel.INFO, "数据库流式导出完成，共导出 " + totalTables + " 个表", null);
				return true;
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "流式导出整个数据库为JSON时发生异常", e);
				return false;
			}
		});
	}
	
	/**
	* 流式导出单个表的数据
	* @param db 数据库连接
	* @param tableName 表名
	* @param writer 输出写入器
	* @param progressListener 进度监听器
	* @return 是否成功导出
	*/
	private boolean exportTableToJsonStream(SQLiteDatabase db, String tableName, Writer writer,
	ExportProgressListener progressListener) {
		Cursor cursor = null;
		
		try {
			// 获取表结构信息
			List<Map<String, String>> tableStructure = mTableManager.getTableStructure(tableName);
			int columnCount = tableStructure.size();
			
			cursor = db.query(tableName, null, null, null, null, null, null);
			
			if (cursor != null) {
				int rowCount = cursor.getCount();
				String[] columnNames = cursor.getColumnNames();
				
				if (progressListener != null) {
					progressListener.onTableSizeDetermined(tableName, rowCount);
				}
				
				// 单行单列：直接写入单个值
				if (rowCount == 1 && columnCount == 1) {
					cursor.moveToFirst();
					Object value = getValueFromCursor(cursor, 0);
					writeJsonValue(writer, value);
				}
				// 单行多列：写入JSONObject
				else if (rowCount == 1 && columnCount > 1) {
					cursor.moveToFirst();
					writer.write("{");
					for (int i = 0; i < columnNames.length; i++) {
						if (i > 0) writer.write(",");
						writer.write("\"" + escapeJsonString(columnNames[i]) + "\":");
						writeJsonValue(writer, getValueFromCursor(cursor, i));
					}
					writer.write("}");
				}
				// 多行：写入JSONArray
				else {
					writer.write("[");
					int rowIndex = 0;
					while (cursor.moveToNext()) {
						if (rowIndex > 0) writer.write(",");
						
						// 单列：直接写入值
						if (columnCount == 1) {
							writeJsonValue(writer, getValueFromCursor(cursor, 0));
						}
						// 多列：写入JSONObject
						else {
							writer.write("{");
							for (int i = 0; i < columnNames.length; i++) {
								if (i > 0) writer.write(",");
								writer.write("\"" + escapeJsonString(columnNames[i]) + "\":");
								writeJsonValue(writer, getValueFromCursor(cursor, i));
							}
							writer.write("}");
						}
						
						if (progressListener != null) {
							progressListener.onRowProcessed(tableName, rowIndex, rowCount);
						}
						
						rowIndex++;
					}
					writer.write("]");
				}
			}
			
			log(DBCipherManager.LogLevel.DEBUG, "表 '" + tableName + "' 数据流式导出完成", null);
			return true;
			
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "流式导出表 '" + tableName + "' 为JSON时发生异常", e);
			return false;
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	* 将值写入JSON流
	* @param writer 输出写入器
	* @param value 要写入的值
	* @throws IOException 如果写入失败
	*/
	private void writeJsonValue(Writer writer, Object value) throws IOException {
		if (value == null || value == JSONObject.NULL) {
			writer.write("null");
		} else if (value instanceof String) {
			writer.write("\"" + escapeJsonString((String) value) + "\"");
		} else if (value instanceof Number) {
			writer.write(value.toString());
		} else if (value instanceof Boolean) {
			writer.write(value.toString());
		} else if (value instanceof byte[]) {
			String base64 = Base64.encodeToString((byte[]) value, Base64.DEFAULT);
			writer.write("\"" + escapeJsonString(base64) + "\"");
		} else {
			writer.write("\"" + escapeJsonString(value.toString()) + "\"");
		}
	}
	
	/**
	* 转义JSON字符串中的特殊字符
	* @param input 原始字符串
	* @return 转义后的字符串
	*/
	private String escapeJsonString(String input) {
		if (input == null) return "";
		
		StringBuilder sb = new StringBuilder();
		for (char c : input.toCharArray()) {
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
				if (c <= '\u001F' || c == '\u007F' || (c >= '\u0080' && c <= '\u009F')) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}
	
	// ==================== 进度监听接口 ====================
	
	/**
	* 导出进度监听器接口
	*/
	public interface ExportProgressListener {
		/**
		* 开始处理表时调用
		* @param tableName 表名
		* @param tableIndex 表索引
		* @param totalTables 总表数
		*/
		void onTableStart(String tableName, int tableIndex, int totalTables);
		
		/**
		* 确定表大小后调用
		* @param tableName 表名
		* @param rowCount 行数
		*/
		void onTableSizeDetermined(String tableName, int rowCount);
		
		/**
		* 处理完一行数据后调用
		* @param tableName 表名
		* @param rowIndex 行索引
		* @param totalRows 总行数
		*/
		void onRowProcessed(String tableName, int rowIndex, int totalRows);
		
		/**
		* 完成表处理时调用
		* @param tableName 表名
		* @param tableIndex 表索引
		* @param totalTables 总表数
		*/
		void onTableComplete(String tableName, int tableIndex, int totalTables);
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
	* 批量插入JSON数据到指定表
	* @param tableName 表名
	* @param jsonArray JSON数组数据
	* @return 成功插入的记录数
	*/
	public int batchInsertDataWithJson(String tableName, JSONArray jsonArray) {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始批量插入JSON数据到表: " + tableName, null);
			
			int insertedCount = 0;
			
			try {
				if (jsonArray == null || jsonArray.length() == 0) {
					log(DBCipherManager.LogLevel.WARN, "JSON数组为空，无需插入", null);
					return 0;
				}
				
				db.beginTransaction();
				try {
					for (int i = 0; i < jsonArray.length(); i++) {
						Object rowData = jsonArray.get(i);
						
						if (rowData instanceof JSONObject) {
							// 多列数据行
							ContentValues values = jsonToContentValues((JSONObject) rowData);
							long result = db.insert(tableName, null, values);
							if (result != -1) {
								insertedCount++;
							}
						} else {
							// 单列数据行
							ContentValues values = new ContentValues();
							values.put(mTableManager.getFirstColumnName(db, tableName), rowData.toString());
							long result = db.insert(tableName, null, values);
							if (result != -1) {
								insertedCount++;
							}
						}
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
				
				log(DBCipherManager.LogLevel.INFO, "批量插入完成，成功插入 " + insertedCount + " 条记录", null);
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "批量插入JSON数据失败", e);
			}
			
			return insertedCount;
		});
	}
	
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