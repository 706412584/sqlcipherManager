package game.core;

import android.content.ContentValues;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
* 数值字段安全更新器 - 支持多字段原子更新和自定义WHERE条件
* 使用与TableManager相同的日志模式，确保日志一致性
*/
public class NumericFieldUpdater {
	private static final String TAG = "NumericFieldUpdater";
	private final DBCipherManager dbManager;
	
	// 锁管理器，确保同一组条件的并发安全
	private final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();
	private final Object lockMapMutex = new Object();
	
	public NumericFieldUpdater(DBCipherManager dbManager) {
		this.dbManager = dbManager;
	}
	
	/**
	* 安全增加数值字段（基于ID）
	* @return 更新后的值，-1表示失败
	*/
	public long safeIncrement(String tableName, String recordId, String fieldName, long increment) {
		return updateField(tableName, "id = ?", new String[]{recordId}, fieldName, increment);
	}
	
	/**
	* 安全减少数值字段（基于ID）
	* @return 更新后的值，-1表示失败，-2表示数值不足
	*/
	public long safeDecrement(String tableName, String recordId, String fieldName, long decrement) {
		return updateField(tableName, "id = ?", new String[]{recordId}, fieldName, -decrement);
	}
	
	/**
	* 安全更新数值字段（完全自定义条件）
	* @param whereClause WHERE条件（如 "user_id = ? AND item_type = ?"）
	* @param whereArgs 条件参数
	* @return 更新后的值
	*/
	public long safeUpdate(String tableName, String whereClause, String[] whereArgs,
	String fieldName, long delta) {
		return updateField(tableName, whereClause, whereArgs, fieldName, delta);
	}
	
	/**
	* 核心更新方法（原子操作）- 支持自定义WHERE条件
	*/
	private long updateField(String tableName, String whereClause, String[] whereArgs,
	String fieldName, long delta) {
		// 生成唯一的锁标识键
		String lockKey = generateLockKey(tableName, fieldName, whereClause);
		Lock lock = acquireLock(lockKey);
		
		try {
			return dbManager.executeWithConnection(db -> {
				try {
					log(DBCipherManager.LogLevel.DEBUG,
					"开始更新字段: " + fieldName + ", 条件: " + whereClause + ", 变化量: " + delta, null);
					
					db.beginTransaction();
					
					// 1. 获取当前值（使用自定义WHERE条件）
					long currentValue = getCurrentValue(db, tableName, whereClause, whereArgs, fieldName);
					if (currentValue == -1) {
						log(DBCipherManager.LogLevel.ERROR, "未找到匹配的记录", null);
						return -1L;
					}
					
					// 2. 检查减量操作是否会导致负值
					if (delta < 0 && currentValue < Math.abs(delta)) {
						log(DBCipherManager.LogLevel.ERROR,
						"数值不足: " + fieldName + "=" + currentValue + ", 尝试减少: " + Math.abs(delta), null);
						return -2L;
					}
					
					// 3. 计算新值
					long newValue = currentValue + delta;
					
					// 4. 执行更新（使用自定义WHERE条件）
					ContentValues values = new ContentValues();
					values.put(fieldName, newValue);
					
					int updated = db.update(tableName, values, whereClause, whereArgs);
					
					if (updated == 1) {
						db.setTransactionSuccessful();
						log(DBCipherManager.LogLevel.INFO,
						"字段更新成功: " + fieldName + "=" + newValue + ", 条件: " + whereClause, null);
						return newValue;
					} else if (updated == 0) {
						log(DBCipherManager.LogLevel.WARN,
						"更新失败：未找到匹配的记录，条件: " + whereClause, null);
						return -1L;
					} else {
						log(DBCipherManager.LogLevel.ERROR,
						"更新异常，影响行数: " + updated + "，预期为1行", null);
						return -1L;
					}
				} catch (Exception e) {
					log(DBCipherManager.LogLevel.ERROR,
					"更新字段时发生异常: " + fieldName + ", 条件: " + whereClause, e);
					return -1L;
				} finally {
					db.endTransaction();
				}
			});
		} finally {
			releaseLock(lockKey, lock);
		}
	}
	
	/**
	* 多字段同时更新（自定义条件）
	*/
	public boolean updateMultipleFields(String tableName, String whereClause, String[] whereArgs,
	Map<String, Long> fieldUpdates) {
		return dbManager.executeWithConnection(db -> {
			try {
				log(DBCipherManager.LogLevel.DEBUG,
				"开始多字段更新: " + fieldUpdates.size() + "个字段, 条件: " + whereClause, null);
				
				db.beginTransaction();
				
				// 1. 获取所有字段当前值
				Map<String, Long> currentValues = getCurrentValues(db, tableName, whereClause, whereArgs,
				fieldUpdates.keySet());
				if (currentValues == null) {
					return false;
				}
				
				// 2. 检查所有字段是否满足更新条件
				for (Map.Entry<String, Long> entry : fieldUpdates.entrySet()) {
					String fieldName = entry.getKey();
					long delta = entry.getValue();
					long currentValue = currentValues.get(fieldName);
					
					if (delta < 0 && currentValue < Math.abs(delta)) {
						log(DBCipherManager.LogLevel.ERROR,
						"数值不足: " + fieldName + "=" + currentValue, null);
						return false;
					}
				}
				
				// 3. 构建更新内容
				ContentValues values = new ContentValues();
				for (Map.Entry<String, Long> entry : fieldUpdates.entrySet()) {
					String fieldName = entry.getKey();
					long delta = entry.getValue();
					long newValue = currentValues.get(fieldName) + delta;
					values.put(fieldName, newValue);
				}
				
				// 4. 执行更新
				int updated = db.update(tableName, values, whereClause, whereArgs);
				
				if (updated == 1) {
					db.setTransactionSuccessful();
					log(DBCipherManager.LogLevel.INFO,
					"多字段更新成功: " + fieldUpdates.size() + "个字段", null);
					return true;
				} else {
					log(DBCipherManager.LogLevel.ERROR,
					"多字段更新失败，影响行数: " + updated, null);
					return false;
				}
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "多字段更新时发生异常", e);
				return false;
			} finally {
				db.endTransaction();
			}
		});
	}
	
	/**
	* 获取当前值（支持自定义WHERE条件）
	*/
	private long getCurrentValue(SQLiteDatabase db, String tableName, String whereClause,
	String[] whereArgs, String fieldName) {
		String sql = "SELECT " + fieldName + " FROM " + tableName;
		if (whereClause != null && !whereClause.trim().isEmpty()) {
			sql += " WHERE " + whereClause;
		}
		
		// 添加LIMIT 1确保只返回一条记录
		sql += " LIMIT 1";
		
		Cursor cursor = null;
		try {
			log(DBCipherManager.LogLevel.DEBUG,
			"查询当前值SQL: " + sql + ", 参数: " + Arrays.toString(whereArgs), null);
			
			cursor = db.rawQuery(sql, whereArgs);
			if (cursor != null && cursor.moveToFirst()) {
				long value = cursor.getLong(0);
				log(DBCipherManager.LogLevel.DEBUG, "查询到当前值: " + value, null);
				return value;
			} else {
				log(DBCipherManager.LogLevel.WARN, "未找到匹配的记录", null);
				return -1;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "查询当前值时发生异常", e);
			return -1;
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	* 获取多个字段当前值（支持自定义WHERE条件）
	*/
	private Map<String, Long> getCurrentValues(SQLiteDatabase db, String tableName,
	String whereClause, String[] whereArgs,
	Iterable<String> fieldNames) {
		// 构建查询字段列表
		StringBuilder fieldsBuilder = new StringBuilder();
		for (String field : fieldNames) {
			fieldsBuilder.append(field).append(", ");
		}
		String fields = fieldsBuilder.substring(0, fieldsBuilder.length() - 2);
		
		String sql = "SELECT " + fields + " FROM " + tableName;
		if (whereClause != null && !whereClause.trim().isEmpty()) {
			sql += " WHERE " + whereClause;
		}
		sql += " LIMIT 1";
		
		Cursor cursor = null;
		try {
			cursor = db.rawQuery(sql, whereArgs);
			if (cursor != null && cursor.moveToFirst()) {
				Map<String, Long> values = new HashMap<>();
				int index = 0;
				for (String field : fieldNames) {
					values.put(field, cursor.getLong(index++));
				}
				return values;
			} else {
				log(DBCipherManager.LogLevel.WARN, "未找到匹配的记录", null);
				return null;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "查询当前值时发生异常", e);
			return null;
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	* 批量更新数值字段（事务内原子操作）
	* @param updates 更新列表
	* @return 是否全部成功
	*/
	public boolean batchUpdate(List<FieldUpdate> updates) {
		return dbManager.executeWithConnection(db -> {
			try {
				log(DBCipherManager.LogLevel.DEBUG,
				"开始批量更新: " + updates.size() + "条记录", null);
				
				db.beginTransaction();
				
				for (FieldUpdate update : updates) {
					// 获取当前值
					long currentValue = getCurrentValue(db, update.tableName,
					update.whereClause, update.whereArgs, update.fieldName);
					if (currentValue == -1) {
						return false;
					}
					
					// 检查减量操作
					if (update.delta < 0 && currentValue < Math.abs(update.delta)) {
						log(DBCipherManager.LogLevel.ERROR,
						"数值不足: " + update.fieldName + "=" + currentValue, null);
						return false;
					}
					
					// 计算新值
					long newValue = currentValue + update.delta;
					
					// 执行更新
					ContentValues values = new ContentValues();
					values.put(update.fieldName, newValue);
					
					int updated = db.update(
					update.tableName,
					values,
					update.whereClause,
					update.whereArgs
					);
					
					if (updated != 1) {
						log(DBCipherManager.LogLevel.ERROR,
						"更新失败: " + update, null);
						return false;
					}
				}
				
				db.setTransactionSuccessful();
				log(DBCipherManager.LogLevel.INFO,
				"批量更新成功: " + updates.size() + "条记录", null);
				return true;
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR,
				"批量更新失败", e);
				return false;
			} finally {
				db.endTransaction();
			}
		});
	}
	
	/**
	* 字段更新描述类（支持自定义条件）
	*/
	public static class FieldUpdate {
		public String tableName;
		public String whereClause;
		public String[] whereArgs;
		public String fieldName;
		public long delta;
		
		public FieldUpdate(String tableName, String whereClause, String[] whereArgs,
		String fieldName, long delta) {
			this.tableName = tableName;
			this.whereClause = whereClause;
			this.whereArgs = whereArgs;
			this.fieldName = fieldName;
			this.delta = delta;
		}
		
		@Override
		public String toString() {
			return tableName + "." + fieldName + "(" + whereClause + "): " + (delta >= 0 ? "+" : "") + delta;
		}
	}
	
	// ==================== 锁管理方法 ====================
	
	/**
	* 生成唯一的锁标识键
	*/
	private String generateLockKey(String tableName, String fieldName, String whereClause) {
		return tableName + ":" + fieldName + ":" +
		(whereClause != null ? whereClause.hashCode() : "default");
	}
	
	/**
	* 获取条件特定的锁
	*/
	private Lock acquireLock(String lockKey) {
		ReentrantLock lock;
		synchronized (lockMapMutex) {
			lock = lockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
		}
		lock.lock();
		return lock;
	}
	
	/**
	* 释放条件特定的锁
	*/
	private void releaseLock(String lockKey, Lock lock) {
		lock.unlock();
		// 可选：清理长时间未使用的锁
	}
	
	// ==================== 日志辅助方法 ====================
	
	private void log(DBCipherManager.LogLevel level, String message, Throwable throwable) {
		dbManager.log(level, TAG, message, throwable);
	}
}


/*
1.基于数据id更新
// 增加仙石
long newValue = numericUpdater.safeIncrement(
"基础属性",
"706412584",
"仙石",
500
);

// 减少灵石
long result = numericUpdater.safeDecrement(
"基础属性",
"706412584",
"灵石",
100
);

2. 自定义条件更新
// 更新特定用户的仙石
long result = numericUpdater.safeUpdate(
"玩家资源",
"user_id = ? AND server_id = ?",
new String[]{"1001", "s1"},
"仙石",
500
);

// 更新特定物品的数量
long result = numericUpdater.safeUpdate(
"背包物品",
"user_id = ? AND item_id = ?",
new String[]{"1001", "2001"},
"数量",
10
);

3. 多字段同时更新
Map<String, Long> updates = new HashMap<>();
updates.put("仙石", 500L);
updates.put("灵石", -100L);

boolean success = numericUpdater.updateMultipleFields(
"玩家资源",
"user_id = ? AND server_id = ?",
new String[]{"1001", "s1"},
updates
);


4. 批量更新
List<NumericFieldUpdater.FieldUpdate> batchUpdates = new ArrayList<>();
batchUpdates.add(new NumericFieldUpdater.FieldUpdate(
"基础属性", "id = ?", new String[]{"706412584"}, "仙石", 500
));
batchUpdates.add(new NumericFieldUpdater.FieldUpdate(
"基础属性", "id = ?", new String[]{"706412584"}, "灵石", -100
));
batchUpdates.add(new NumericFieldUpdater.FieldUpdate(
"战斗属性", "user_id = ?", new String[]{"1001"}, "攻击", 10
));

boolean batchSuccess = numericUpdater.batchUpdate(batchUpdates);



*/