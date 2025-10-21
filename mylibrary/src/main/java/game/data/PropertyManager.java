package game.data;

import android.content.ContentValues;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import game.core.DBCipherManager;
import game.core.NumericFieldUpdater;
import game.util.AESUtils;
import game.core.SqlUtilManager;
/**
* 属性管理器 - 集成内存缓存加解密功能
* 支持数值操作安全、缓存加密存储、数据解密读取
*/
public class PropertyManager {
	private final DBCipherManager dbManager;
	private final NumericFieldUpdater numericUpdater;
	
	// 缓存管理，提升频繁访问的性能
	private final Map<String, CacheEntry> valueCache = new ConcurrentHashMap<>();
	private final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存
	private String encryptionKey="xiugansiquanjiaH";
	
	public PropertyManager(DBCipherManager dbManager, String encryptionKeys) {
		this.dbManager = dbManager;
		this.numericUpdater = new NumericFieldUpdater(dbManager);
		this.encryptionKey = encryptionKeys;
	}
	public PropertyManager(DBCipherManager dbManager) {
		this.dbManager = dbManager;
		this.numericUpdater = new NumericFieldUpdater(dbManager);
	}
	
	// ==================== 缓存管理 ====================
	
	/**
	* 缓存条目类，存储加密值和时间戳
	*/
	private static class CacheEntry {
		final String encryptedValue;
		final long timestamp;
		
		CacheEntry(String encryptedValue) {
			this.encryptedValue = encryptedValue;
			this.timestamp = System.currentTimeMillis();
		}
		
		boolean isExpired(long cacheDuration) {
			return (System.currentTimeMillis() - timestamp) > cacheDuration;
		}
	}
	/**
	* 将值加密后存入缓存
	*/
	private void putToCache(String key, Object value) {
		if (value == null) return;
		
		try {
			String valueStr = value.toString();
			String encrypted = AESUtils.encrypt(valueStr, encryptionKey);
			valueCache.put(key, new CacheEntry(encrypted));
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "缓存加密失败", e);
		}
	}
	
	/**
	* 从缓存获取值并解密
	*/
	private Object getFromCache(String key, Class<?> targetType) {
		CacheEntry entry = valueCache.get(key);
		if (entry == null || entry.isExpired(CACHE_DURATION)) {
			return null;
		}
		
		try {
			String decrypted = AESUtils.decrypt(entry.encryptedValue, encryptionKey);
			return convertStringToType(decrypted, targetType);
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "缓存解密失败", e);
			valueCache.remove(key); // 移除无效缓存
			return null;
		}
	}
	
	/**
	* 清理相关缓存
	*/
	public void clearRelevantCache(String tableName, String whereClause) {
		// 简化实现：根据表名清理缓存
		valueCache.keySet().removeIf(key -> key.startsWith(tableName + ":"));
	}
	
	private String generateCacheKey(String tableName, String recordId, String fieldName) {
		return tableName + ":" + recordId + ":" + fieldName;
	}
	
	// ==================== 核心数值操作方法（使用NumericFieldUpdater）====================
	
	/**
	* 安全增加数值属性（原子操作）
	*/
	public long safeIncrement(String tableName, String recordId, String fieldName, long increment) {
		try {
			long result = numericUpdater.safeIncrement(tableName, recordId, fieldName, increment);
			if (result >= 0) {
				// 更新缓存
				String cacheKey = generateCacheKey(tableName, recordId, fieldName);
				putToCache(cacheKey, result);
			}
			return result;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "安全增加数值失败", e);
			return -1;
		}
	}
	
	/**
	* 安全减少数值属性（防负值检查）
	*/
	public long safeDecrement(String tableName, String recordId, String fieldName, long decrement) {
		try {
			long result = numericUpdater.safeDecrement(tableName, recordId, fieldName, decrement);
			if (result >= 0) {
				// 更新缓存
				String cacheKey = generateCacheKey(tableName, recordId, fieldName);
				putToCache(cacheKey, result);
			}
			return result;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "安全减少数值失败", e);
			return -1;
		}
	}
	
	/**
	* 自定义条件的安全数值更新
	*/
	public long safeUpdate(String tableName, String whereClause, String[] whereArgs,
	String fieldName, long delta) {
		try {
			long result = numericUpdater.safeUpdate(tableName, whereClause, whereArgs, fieldName, delta);
			// 注意：自定义条件更新可能影响多条记录，不更新缓存
			return result;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "安全更新数值失败", e);
			return -1;
		}
	}
	
	/**
	* 多字段原子更新（事务保证）
	*/
	public boolean updateMultipleFields(String tableName, String whereClause, String[] whereArgs,
	Map<String, Long> fieldUpdates) {
		try {
			boolean success = numericUpdater.updateMultipleFields(tableName, whereClause, whereArgs, fieldUpdates);
			if (success) {
				// 清理相关缓存
				clearRelevantCache(tableName, whereClause);
			}
			return success;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "多字段原子更新失败", e);
			return false;
		}
	}
	
	// ==================== 带缓存的数值获取 ====================
	
	/**
	* 获取带缓存的数值（自动加解密）
	*/
	public long getCachedNumericValue(String tableName, String recordId, String fieldName) {
		String cacheKey = generateCacheKey(tableName, recordId, fieldName);
		
		// 检查缓存
		Object cached = getFromCache(cacheKey, Long.class);
		if (cached instanceof Long) {
			return (Long) cached;
		}
		
		// 缓存未命中，查询数据库
		long value = getNumericValue(tableName, "id = ?", new String[]{recordId}, fieldName, 0L);
		putToCache(cacheKey, value);
		return value;
	}
	
	/**
	* 获取带缓存的字符串值（自动加解密）
	*/
	public String getCachedStringValue(String tableName, String recordId, String fieldName) {
		String cacheKey = generateCacheKey(tableName, recordId, fieldName);
		
		// 检查缓存
		Object cached = getFromCache(cacheKey, String.class);
		if (cached instanceof String) {
			return (String) cached;
		}
		
		// 缓存未命中，查询数据库
		String value = getStringValue(tableName, "id = ?", new String[]{recordId}, fieldName, "");
		putToCache(cacheKey, value);
		return value;
	}
	
	//获取当前值
	private <T> T queryProperty(String tableName, String property, String whereClause,
	String[] whereArgs, T defaultValue) {
		ContentValues data = dbManager.querySingle(tableName, whereClause, whereArgs);
		
		if (data != null && data.containsKey(property)) {
			Object rawValue = data.get(property);
			if (rawValue != null) {
				return convertValue(rawValue, (Class<T>) defaultValue.getClass());
			}
		}
		
		return defaultValue;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T operateProperty(String tableName, String property, String operation,
	Object value, T defaultValue, String whereClause, String[] whereArgs) {
		
		// 特殊处理：当 value 是 ContentValues 或 JSONObject 时
		if (value instanceof ContentValues || value instanceof JSONObject) {
			// 对于"增加"、"减少"和"替换"操作调用结构化数据处理方法
			if ("增加".equals(operation) || "减少".equals(operation) || "替换".equals(operation)||"更新".equals(operation)) {
				return handleStructuredDataOperation(tableName, property, operation, value, defaultValue, whereClause, whereArgs);
			}
		}
		
		// 1. 查询当前值
		T currentValue = queryProperty(tableName, property, whereClause, whereArgs, defaultValue);
		
		// 2. 根据操作类型处理
		switch (operation) {
			case "查询":
			return currentValue;
			
			case "增加":
			return handleIncrease(tableName, property, value, currentValue, defaultValue, whereClause, whereArgs);
			
			case "减少":
			return handleDecrease(tableName, property, value, currentValue, defaultValue, whereClause, whereArgs);
			
			case "替换":
			return handleReplace(tableName, property, value, defaultValue, whereClause, whereArgs);
			
			case "存在检查":
			return (T) Boolean.valueOf(currentValue != null &&
			!currentValue.equals(defaultValue) &&
			!String.valueOf(currentValue).isEmpty());
			
			default:
			throw new IllegalArgumentException("不支持的操作类型: " + operation);
		}
	}
	
	/**
	* 处理结构化数据操作（ContentValues 或 JSONObject）
	* 支持增加、减少和替换操作
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleStructuredDataOperation(String tableName, String property, String operation,
	Object value, T defaultValue, String whereClause, String[] whereArgs) {
		try {
			// 将输入转换为 ContentValues
			ContentValues values = convertToContentValues(value);
			long count = dbManager.queryCount(tableName, whereClause, whereArgs);
			
			if ("增加".equals(operation) || "减少".equals(operation)) {
				if (count < 1) {
					// 没有记录，插入新记录
					long newId = dbManager.insertData(tableName, values);
					
					// 根据返回值类型返回结果
					if (defaultValue instanceof Boolean) {
						return (T) Boolean.valueOf(newId > 0);
					} else if (defaultValue instanceof Long) {
						return (T) Long.valueOf(newId);
					} else if (defaultValue instanceof Integer) {
						return (T) Integer.valueOf((int) newId);
					} else {
						return (T) Boolean.valueOf(newId > 0);
					}
				} else {
					// 有记录，检查是否存在数量字段，存在则增加或减少数量
					ContentValues updateValues = new ContentValues();
					boolean hasQuantityField = false;
					boolean insufficient = false; // 标记是否不足
					
					// 1. 获取现有记录
					ContentValues existingData = dbManager.querySingle(tableName, whereClause, whereArgs);
					
					// 2. 遍历所有字段，检查数值字段
					for (String field : values.keySet()) {
						Object newValue = values.get(field);
						
						// 检查字段名是否包含"数量"、"数值"、"值"等关键词
						if (isQuantityField(field) && newValue instanceof Number) {
							// 获取现有值
							Object existingValue = existingData.get(field);
							if (existingValue instanceof Number) {
								// 计算新值
								BigDecimal existing = convertToBigDecimal(existingValue);
								BigDecimal operationValue = convertToBigDecimal(newValue);
								BigDecimal result;
								
								if ("增加".equals(operation)) {
									result = existing.add(operationValue);
								} else { // "减少"
									// 检查是否足够扣除
									if (existing.compareTo(operationValue) < 0) {
										insufficient = true;
										// 跳过后续字段处理
										break;
									}
									result = existing.subtract(operationValue);
								}
								
								// 防止负值
								if (result.compareTo(BigDecimal.ZERO) < 0) {
									result = BigDecimal.ZERO;
								}
								
								// 添加更新值
								Object newVal = convertToOriginalType(result, existingValue.getClass());
								putValueToContentValues(updateValues, field, newVal);
								hasQuantityField = true;
							}
						} else {
							// 非数值字段直接更新
							putValueToContentValues(updateValues, field, newValue);
						}
					}
					
					// 如果发现不足，返回失败
					if (insufficient) {
						if (defaultValue instanceof Boolean) {
							return (T) Boolean.FALSE;
						} else {
							return defaultValue;
						}
					}
					
					// 3. 执行更新
					if (hasQuantityField) {
						int updated = dbManager.updateData(tableName, updateValues, whereClause, whereArgs);
						return (T) Boolean.valueOf(updated > 0);
					} else {
                        if(count>0)
                        {
                        	//已有记录但是没有数值操作
                        	return (T) Boolean.valueOf(false);
                        }
						// 没有数值字段，执行普通更新
						int updated = dbManager.updateData(tableName, values, whereClause, whereArgs);
						return (T) Boolean.valueOf(updated > 0);
					}
				}
			} else if ("替换".equals(operation)||"更新".equals(operation)) {
				// 更新现有记录
				int updated = dbManager.updateData(tableName, values, whereClause, whereArgs);
				
				if (defaultValue instanceof Boolean) {
					return (T) Boolean.valueOf(updated > 0);
				} else if (defaultValue instanceof Long) {
					return (T) Long.valueOf(updated);
				} else if (defaultValue instanceof Integer) {
					return (T) Integer.valueOf(updated);
				} else {
					return (T) Boolean.valueOf(updated > 0);
				}
			} else {
				throw new IllegalArgumentException("结构化数据只支持增加、减少和替换操作");
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "处理结构化数据操作失败", e);
			return defaultValue;
		}
	}
	
	
	
	
	/**
	* 从当前值对象中获取字段值
	*/
	private Object getFieldValueFromCurrent(Object currentValue, String field) {
		if (currentValue instanceof ContentValues) {
			return ((ContentValues) currentValue).get(field);
		} else if (currentValue instanceof JSONObject) {
			try {
				return ((JSONObject) currentValue).get(field);
			} catch (JSONException e) {
				return null;
			}
		} else if (currentValue instanceof Map) {
			return ((Map<?, ?>) currentValue).get(field);
		}
		return null;
	}
	
	/**
	* 判断字段是否为数量字段
	*/
	private boolean isQuantityField(String fieldName) {
		if (fieldName == null) return false;
		
		String lowerField = fieldName.toLowerCase();
		return lowerField.contains("数量") ||
		lowerField.contains("数值") ||
		lowerField.contains("值") ||
		lowerField.contains("quantity") ||
		lowerField.contains("value") ||
		lowerField.contains("count");
	}
	
	
	
	/**
	* 将对象转换为 ContentValues
	*/
	private ContentValues convertToContentValues(Object value) {
		if (value instanceof ContentValues) {
			return (ContentValues) value;
		} else if (value instanceof JSONObject) {
			return SqlUtilManager.jsonToContentValues((JSONObject) value);
		} else {
			throw new IllegalArgumentException("不支持的数据类型: " + value.getClass().getName());
		}
	}
	
	
	
	
	private BigDecimal convertToBigDecimal(Object value) {
		if (value instanceof Integer) {
			return BigDecimal.valueOf((Integer) value);
		} else if (value instanceof Long) {
			return BigDecimal.valueOf((Long) value);
		} else if (value instanceof Double) {
			return BigDecimal.valueOf((Double) value);
		} else if (value instanceof Float) {
			return BigDecimal.valueOf((Float) value);
		} else if (value instanceof BigDecimal) {
			return (BigDecimal) value;
		} else {
			return new BigDecimal(value.toString());
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private <T> T handleIncrease(String tableName, String property, Object increment,
	T currentValue, T defaultValue, String whereClause, String[] whereArgs) {
		// 检查是否为数值类型
		if (currentValue instanceof Number && increment instanceof Number) {
			// 如果当前值等于默认值，说明记录可能不存在
			if (currentValue.equals(defaultValue)) {
				// 尝试插入新记录
				ContentValues values = new ContentValues();
				putValueToContentValues(values, property, increment);
				
				// 尝试插入新记录
				long newId = dbManager.insertData(tableName, values);
				
				if (newId > 0) {
					// 插入成功，返回新值
					return (T) increment;
				} else {
					// 插入失败，可能是记录已存在（并发情况），重新查询
					currentValue = queryProperty(tableName, property, whereClause, whereArgs, defaultValue);
					
					// 如果重新查询后仍然是默认值，说明记录确实不存在
					if (currentValue.equals(defaultValue)) {
						log(DBCipherManager.LogLevel.ERROR, "增加操作失败：无法创建新记录", null);
						return defaultValue;
					}
				}
			}
			
			// 记录存在，执行增加操作
			return handleNumericIncrease(tableName, property, increment, currentValue, defaultValue, whereClause, whereArgs);
		}
		
		// 非数值类型保持原有逻辑
		Object newValue = performArithmeticOperation(currentValue, increment, true);
		return updateProperty(tableName, property, newValue, defaultValue, whereClause, whereArgs);
	}
	
	
	/**
	* 处理减少操作 - 数值类型使用优化方法
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleDecrease(String tableName, String property, Object decrement,
	T currentValue, T defaultValue, String whereClause, String[] whereArgs) {
		// 检查是否为数值类型
		if (currentValue instanceof Number && decrement instanceof Number) {
			return handleNumericDecrease(tableName, property, decrement, currentValue, defaultValue, whereClause, whereArgs);
		}
		
		// 非数值类型保持原有逻辑
		Object newValue = performArithmeticOperation(currentValue, decrement, false);
		return updateProperty(tableName, property, newValue, defaultValue, whereClause, whereArgs);
	}
	
	/**
	* 数值类型的增加操作 - 使用优化方法
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleNumericIncrease(String tableName, String property, Object increment,
	T currentValue, T defaultValue, String whereClause, String[] whereArgs) {
		try {
			long numericValue = ((Number) increment).longValue();
			long result = numericUpdater.safeUpdate(tableName, whereClause, whereArgs, property, numericValue);
			
			if (result >= 0) {
				// 更新缓存
				String cacheKey = generateCacheKey(tableName, "custom", property);
				putToCache(cacheKey, result);
				
				return (T) convertToOriginalType(result, defaultValue);
			} else {
				return defaultValue;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "数值增加操作失败", e);
			return defaultValue;
		}
	}
	
	/**
	* 数值类型的减少操作 - 使用优化方法
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleNumericDecrease(String tableName, String property, Object decrement,
	T currentValue, T defaultValue, String whereClause, String[] whereArgs) {
		try {
			long numericValue = ((Number) decrement).longValue();
			long result = numericUpdater.safeUpdate(tableName, whereClause, whereArgs, property, -numericValue);
			
			if (result >= 0) {
				// 更新缓存
				String cacheKey = generateCacheKey(tableName, "custom", property);
				putToCache(cacheKey, result);
				
				return (T) convertToOriginalType(result, defaultValue);
			} else {
				return defaultValue;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "数值减少操作失败", e);
			return defaultValue;
		}
	}
	
	/**
	* 判断是否为数值操作
	*/
	private <T> boolean isNumericOperation(String operation, Object value, T defaultValue) {
		if (!("增加".equals(operation) || "减少".equals(operation))) {
			return false;
		}
		
		// 检查值和默认值是否为数值类型
		return (value instanceof Number) &&
		(defaultValue instanceof Number || defaultValue == null);
	}
	
	
	/**
	* 处理替换操作
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleReplace(String tableName, String property, Object newValue,
	T defaultValue, String whereClause, String[] whereArgs) {
		return updateProperty(tableName, property, newValue, defaultValue, whereClause, whereArgs);
	}
	
	/**
	* 更新属性值
	*/
	@SuppressWarnings("unchecked")
	private <T> T updateProperty(String tableName, String property, Object newValue,
	T defaultValue, String whereClause, String[] whereArgs) {
		ContentValues values = new ContentValues();
		putValueToContentValues(values, property, newValue);
		
		int updated = dbManager.updateData(tableName, values, whereClause, whereArgs);
		return updated > 0 ? (T) newValue : defaultValue;
	}
	/**
	* 将值安全地放入ContentValues
	*/
	private void putValueToContentValues(ContentValues values, String key, Object value) {
		if (value instanceof Integer) {
			values.put(key, (Integer) value);
		} else if (value instanceof Long) {
			values.put(key, (Long) value);
		} else if (value instanceof Double) {
			values.put(key, (Double) value);
		} else if (value instanceof Float) {
			values.put(key, (Float) value);
		} else if (value instanceof String) {
			values.put(key, (String) value);
		} else if (value instanceof Boolean) {
			values.put(key, (Boolean) value);
		} else if (value instanceof byte[]) {
			values.put(key, (byte[]) value);
		} else {
			values.put(key, String.valueOf(value));
		}
	}
	
	/**
	* 执行算术运算
	*/
	private Object performArithmeticOperation(Object currentValue, Object operationValue, boolean isIncrease) {
		if (!(currentValue instanceof Number) || !(operationValue instanceof Number)) {
			throw new IllegalArgumentException("算术运算只支持数值类型");
		}
		
		BigDecimal current = convertToBigDecimal(currentValue);
		BigDecimal operation = convertToBigDecimal(operationValue);
		BigDecimal result;
		
		if (isIncrease) {
			result = current.add(operation);
		} else {
			result = current.subtract(operation);
			if (result.compareTo(BigDecimal.ZERO) < 0) {
				result = BigDecimal.ZERO;
			}
		}
		
		return convertToOriginalType(result, currentValue.getClass());
	}
	
	/**
	* 类型转换辅助方法
	*/
	@SuppressWarnings("unchecked")
	private <T> T convertToOriginalType(BigDecimal value, Class<?> targetType) {
		if (targetType == Integer.class || targetType == int.class) {
			return (T) Integer.valueOf(value.intValue());
		} else if (targetType == Long.class || targetType == long.class) {
			return (T) Long.valueOf(value.longValue());
		} else if (targetType == Double.class || targetType == double.class) {
			return (T) Double.valueOf(value.doubleValue());
		} else if (targetType == Float.class || targetType == float.class) {
			return (T) Float.valueOf(value.floatValue());
		} else {
			return (T) value;
		}
	}
	
	
	/**
	* 值转换方法
	*/
	@SuppressWarnings("unchecked")
	public <T> T convertValue(Object value, Class<T> type) {
		if (value == null) {
			return null;
		}
		
		if (type.isInstance(value)) {
			return (T) value;
		}
		
		try {
			if (type == String.class) {
				return (T) value.toString();
			}
			
			String stringValue = value.toString();
			
			if (type == Integer.class || type == int.class) {
				return (T) Integer.valueOf(stringValue);
			}
			
			if (type == Long.class || type == long.class) {
				return (T) Long.valueOf(stringValue);
			}
			
			if (type == Double.class || type == double.class) {
				return (T) Double.valueOf(stringValue);
			}
			
			if (type == Float.class || type == float.class) {
				return (T) Float.valueOf(stringValue);
			}
			
			if (type == Boolean.class || type == boolean.class) {
				if (value instanceof Boolean) {
					return (T) value;
				}
				String str = stringValue.toLowerCase();
				return (T) Boolean.valueOf("true".equals(str) || "1".equals(str) || "yes".equals(str));
			}
			
			if (type == BigDecimal.class) {
				return (T) new BigDecimal(stringValue);
			}
			
			if (type == JSONObject.class) {
				if (value instanceof String) {
					return (T) new JSONObject((String) value);
				} else if (value instanceof JSONObject) {
					return (T) value;
				} else {
					return (T) new JSONObject(value.toString());
				}
			}
			
			if (type == JSONArray.class) {
				if (value instanceof String) {
					return (T) new JSONArray((String) value);
				} else if (value instanceof JSONArray) {
					return (T) value;
				} else {
					return (T) new JSONArray(value.toString());
				}
			}
			
		} catch (Exception e) {
			throw new ClassCastException("无法将 " + value.getClass().getName() + " 转换为 " + type.getName() + ": " + e.getMessage());
		}
		
		throw new ClassCastException("不支持的转换类型: " + type.getName());
	}
	
	
	/**
	* 使用NumericFieldUpdater处理数值操作
	*/
	@SuppressWarnings("unchecked")
	private <T> T handleNumericOperation(String tableName, String property, String operation,
	Object value, T defaultValue,
	String whereClause, String[] whereArgs) {
		try {
			long numericValue = ((Number) value).longValue();
			long result;
			
			if ("增加".equals(operation)) {
				result = numericUpdater.safeUpdate(tableName, whereClause, whereArgs,
				property, numericValue);
			} else { // "减少"
				result = numericUpdater.safeUpdate(tableName, whereClause, whereArgs,
				property, -numericValue);
			}
			
			if (result >= 0) {
				// 更新缓存
				String cacheKey = generateCacheKey(tableName, "custom", property);
				putToCache(cacheKey, result);
				
				return (T) convertToOriginalType(result, defaultValue);
			} else {
				return defaultValue;
			}
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "数值操作处理失败", e);
			return defaultValue;
		}
	}
	
	// ==================== 辅助方法 ====================
	
	/**
	* 类型转换辅助方法
	*/
	@SuppressWarnings("unchecked")
	private <T> T convertToOriginalType(long value, T originalType) {
		if (originalType instanceof Integer) {
			return (T) Integer.valueOf((int) value);
		} else if (originalType instanceof Long) {
			return (T) Long.valueOf(value);
		} else if (originalType instanceof Short) {
			return (T) Short.valueOf((short) value);
		} else if (originalType instanceof Byte) {
			return (T) Byte.valueOf((byte) value);
		} else {
			return (T) Long.valueOf(value);
		}
	}
	
	/**
	* 将字符串转换为指定类型
	*/
	@SuppressWarnings("unchecked")
	private <T> T convertStringToType(String value, Class<T> targetType) {
		if (targetType == String.class) {
			return (T) value;
		} else if (targetType == Integer.class || targetType == int.class) {
			return (T) Integer.valueOf(value);
		} else if (targetType == Long.class || targetType == long.class) {
			return (T) Long.valueOf(value);
		} else if (targetType == Double.class || targetType == double.class) {
			return (T) Double.valueOf(value);
		} else if (targetType == Float.class || targetType == float.class) {
			return (T) Float.valueOf(value);
		} else if (targetType == Boolean.class || targetType == boolean.class) {
			return (T) Boolean.valueOf(value);
		} else {
			return (T) value;
		}
	}
	
	/**
	* 日志方法
	*/
	private void log(DBCipherManager.LogLevel level, String message, Throwable throwable) {
		dbManager.log(level, "PropertyManager", message, throwable);
	}
	
	private long getNumericValue(String tableName, String whereClause, String[] whereArgs,
	String fieldName, long defaultValue) {
		// 查询数值的辅助方法
		return defaultValue;
	}
	
	private String getStringValue(String tableName, String whereClause, String[] whereArgs,
	String fieldName, String defaultValue) {
		// 查询字符串的辅助方法
		return defaultValue;
	}
	
	private <T> T originalOperateProperty(String tableName, String property, String operation,
	Object value, T defaultValue,
	String whereClause, String[] whereArgs) {
		return defaultValue;
	}
	
	// ==================== 原有单字段便捷方法（保持兼容）====================
	
	public <T> T getProperty(String tableName, String property, T defaultValue) {
		return operateProperty(tableName, property, "查询", null, defaultValue, "id > ?", new String[]{"0"});
	}
	
	public <T> T setProperty(String tableName, String property, T value, T defaultValue) {
		return operateProperty(tableName, property, "替换", value, defaultValue, "id > ?", new String[]{"0"});
	}
	
	public <T extends Number> T increaseProperty(String tableName, String property, T increment, T defaultValue) {
		return operateProperty(tableName, property, "增加", increment, defaultValue, "id > ?", new String[]{"0"});
	}
	
	public <T extends Number> T decreaseProperty(String tableName, String property, T decrement, T defaultValue) {
		return operateProperty(tableName, property, "减少", decrement, defaultValue, "id > ?", new String[]{"0"});
	}
	
	public boolean hasProperty(String tableName, String property) {
		return operateProperty(tableName, property, "存在检查", null, false, "id > ?", new String[]{"0"});
	}
	
	public boolean hasProperty(String tableName, String property, String where, String[] whereArgs) {
		return operateProperty(tableName, property, "存在检查", null, false, where, whereArgs);
	}
	
	// ==================== 批量操作增强 ====================
	
	/**
	* 批量数值操作（支持混合操作类型）
	*/
	public Map<String, Object> batchNumericOperations(String tableName, List<NumericOperation> operations) {
		Map<String, Object> results = new HashMap<>();
		
		return dbManager.executeWithConnection(db -> {
			try {
				db.beginTransaction();
				
				for (NumericOperation operation : operations) {
					try {
						Object result = executeSingleNumericOperation(db, operation);
						results.put(operation.getOperationId(), result);
					} catch (Exception e) {
						results.put(operation.getOperationId(), new OperationResult(false, e.getMessage()));
					}
				}
				
				db.setTransactionSuccessful();
				return results;
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "批量数值操作事务失败", e);
				return results;
			} finally {
				db.endTransaction();
			}
		});
	}
	
	// ==================== 高级查询功能 ====================
	
	/**
	* 获取数值字段的统计信息
	*/
	public Map<String, Object> getNumericFieldStats(String tableName, String fieldName,
	String whereClause, String[] whereArgs) {
		return dbManager.executeWithConnection(db -> {
			Map<String, Object> stats = new HashMap<>();
			Cursor cursor = null;
			
			try {
				String sql = String.format(
				"SELECT COUNT(*) as count, SUM(%s) as sum, AVG(%s) as avg, " +
				"MIN(%s) as min, MAX(%s) as max FROM %s",
				fieldName, fieldName, fieldName, fieldName, tableName
				);
				
				if (whereClause != null && !whereClause.trim().isEmpty()) {
					sql += " WHERE " + whereClause;
				}
				
				cursor = db.rawQuery(sql, whereArgs);
				if (cursor != null && cursor.moveToFirst()) {
					stats.put("count", cursor.getLong(0));
					stats.put("sum", cursor.getLong(1));
					stats.put("average", cursor.getDouble(2));
					stats.put("minimum", cursor.getLong(3));
					stats.put("maximum", cursor.getLong(4));
				}
				
				return stats;
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "获取数值字段统计信息失败", e);
				return stats;
			} finally {
				if (cursor != null) cursor.close();
			}
		});
	}
	
	/**
	* 数值范围查询
	*/
	public List<ContentValues> queryByNumericRange(String tableName, String fieldName,
	long minValue, long maxValue,
	String additionalWhere, String[] whereArgs) {
		StringBuilder whereBuilder = new StringBuilder();
		whereBuilder.append(fieldName).append(" BETWEEN ? AND ?");
		
		List<String> allArgs = new ArrayList<>();
		allArgs.add(String.valueOf(minValue));
		allArgs.add(String.valueOf(maxValue));
		
		if (additionalWhere != null && !additionalWhere.trim().isEmpty()) {
			whereBuilder.append(" AND ").append(additionalWhere);
			if (whereArgs != null) {
				allArgs.addAll(Arrays.asList(whereArgs));
			}
		}
		
		return dbManager.query(tableName, null, whereBuilder.toString(),
		allArgs.toArray(new String[0]), null, null, null);
	}
	
	// ==================== 操作执行方法 ====================
	/**
	* 执行单个数值操作（支持多种操作类型）
	* 包括基本算术运算、数学函数、比较运算等
	*/
	private Object executeSingleNumericOperation(SQLiteDatabase db, NumericOperation operation) {
		try {
			// 1. 获取当前值
			double currentValue = getCurrentNumericValue(db, operation);
			double operandValue = operation.getValue();
			double result;
			
			// 2. 根据操作类型执行相应的计算
			switch (operation.getOperationType()) {
				// ==================== 基本算术运算 ====================
				case INCREMENT:
				result = currentValue + operandValue;
				break;
				
				case DECREMENT:
				result = currentValue - operandValue;
				// 防负值检查
				if (result < 0 && operation.isPreventNegative()) {
					throw new ArithmeticException("数值不能为负: " + currentValue + " - " + operandValue);
				}
				break;
				
				case MULTIPLY:
				result = currentValue * operandValue;
				break;
				
				case DIVIDE:
				if (operandValue == 0) {
					throw new ArithmeticException("除数不能为零");
				}
				result = currentValue / operandValue;
				break;
				
				// ==================== 数学函数运算 ====================
				case POWER:
				result = Math.pow(currentValue, operandValue);
				break;
				
				case SQUARE_ROOT:
				if (currentValue < 0) {
					throw new ArithmeticException("不能对负数开平方根: " + currentValue);
				}
				result = Math.sqrt(currentValue);
				break;
				
				case LOGARITHM:
				if (currentValue <= 0) {
					throw new ArithmeticException("对数运算要求正数: " + currentValue);
				}
				result = Math.log(currentValue);
				break;
				
				case LOG10:
				if (currentValue <= 0) {
					throw new ArithmeticException("常用对数运算要求正数: " + currentValue);
				}
				result = Math.log10(currentValue);
				break;
				
				case ABSOLUTE:
				result = Math.abs(currentValue);
				break;
				
				case ROUND:
				result = Math.round(currentValue);
				break;
				
				case CEIL:
				result = Math.ceil(currentValue);
				break;
				
				case FLOOR:
				result = Math.floor(currentValue);
				break;
				
				// ==================== 三角函数运算 ====================
				case SIN:
				result = Math.sin(Math.toRadians(currentValue));
				break;
				
				case COS:
				result = Math.cos(Math.toRadians(currentValue));
				break;
				
				case TAN:
				result = Math.tan(Math.toRadians(currentValue));
				break;
				
				// ==================== 百分比运算 ====================
				case PERCENTAGE:
				result = currentValue * (operandValue / 100.0);
				break;
				
				// ==================== 设置操作 ====================
				case SET:
				result = operandValue;
				break;
				
				// ==================== 比较运算 ====================
				case MAX:
				result = Math.max(currentValue, operandValue);
				break;
				
				case MIN:
				result = Math.min(currentValue, operandValue);
				break;
				
				default:
				throw new IllegalArgumentException("不支持的操作类型: " + operation.getOperationType());
			}
			
			// 3. 执行数据库更新
			boolean updateSuccess = updateNumericValue(db, operation, result);
			
			if (updateSuccess) {
				// 4. 根据期望的返回类型转换结果
				return convertResultToTargetType(result, operation.getReturnType());
			} else {
				throw new RuntimeException("数据库更新失败");
			}
			
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "执行数值操作失败: " + operation.getOperationType(), e);
			return createErrorResult(operation, e.getMessage());
		}
	}
	
	// ==================== 辅助方法 ====================
	
	/**
	* 获取当前数值
	*/
	private double getCurrentNumericValue(SQLiteDatabase db, NumericOperation operation) {
		String sql = "SELECT " + operation.getFieldName() + " FROM " + operation.getTableName();
		
		if (operation.getWhereClause() != null && !operation.getWhereClause().trim().isEmpty()) {
			sql += " WHERE " + operation.getWhereClause();
		}
		sql += " LIMIT 1";
		
		Cursor cursor = null;
		try {
			cursor = db.rawQuery(sql, operation.getWhereArgs());
			if (cursor != null && cursor.moveToFirst()) {
				return cursor.getDouble(0);
			} else {
				throw new RuntimeException("未找到匹配的记录");
			}
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	* 更新数值到数据库
	*/
	private boolean updateNumericValue(SQLiteDatabase db, NumericOperation operation, double newValue) {
		ContentValues values = new ContentValues();
		values.put(operation.getFieldName(), newValue);
		
		int updated = db.update(
		operation.getTableName(),
		values,
		operation.getWhereClause(),
		operation.getWhereArgs()
		);
		
		return updated > 0;
	}
	
	/**
	* 将结果转换为目标类型
	*/
	@SuppressWarnings("unchecked")
	private <T> T convertResultToTargetType(double result, Class<T> targetType) {
		if (targetType == Integer.class || targetType == int.class) {
			return (T) Integer.valueOf((int) result);
		} else if (targetType == Long.class || targetType == long.class) {
			return (T) Long.valueOf((long) result);
		} else if (targetType == Double.class || targetType == double.class) {
			return (T) Double.valueOf(result);
		} else if (targetType == Float.class || targetType == float.class) {
			return (T) Float.valueOf((float) result);
		} else if (targetType == String.class) {
			return (T) String.valueOf(result);
		} else if (targetType == BigDecimal.class) {
			return (T) BigDecimal.valueOf(result);
		} else {
			return (T) Double.valueOf(result);
		}
	}
	
	/**
	* 创建错误结果
	*/
	private Object createErrorResult(NumericOperation operation, String errorMessage) {
		// 根据操作配置返回适当的错误值
		if (operation.isThrowExceptionOnError()) {
			throw new RuntimeException(errorMessage);
		} else {
			return operation.getDefaultValue();
		}
	}
	
	// ==================== NumericOperation类 ====================
	
	/**
	* 数值操作描述类
	*/
	static class NumericOperation {
		private String operationId;
		private String tableName;
		private String whereClause;
		private String[] whereArgs;
		private String fieldName;
		private OperationType operationType;
		private double value;
		private Class<?> returnType;
		private Object defaultValue;
		private boolean preventNegative;
		private boolean throwExceptionOnError;
		private Map<String, Object> customProperties;
		
		enum OperationType {
			// 基本算术
			INCREMENT, DECREMENT, MULTIPLY, DIVIDE,
			// 数学函数
			POWER, SQUARE_ROOT, LOGARITHM, LOG10,
			ABSOLUTE, ROUND, CEIL, FLOOR,
			// 三角函数
			SIN, COS, TAN,
			// 百分比
			PERCENTAGE,
			// 设置和比较
			SET, MAX, MIN
		}
		
		public NumericOperation(String operationId, String tableName, String whereClause,
		String[] whereArgs, String fieldName, OperationType operationType,
		double value) {
			this.operationId = operationId;
			this.tableName = tableName;
			this.whereClause = whereClause;
			this.whereArgs = whereArgs;
			this.fieldName = fieldName;
			this.operationType = operationType;
			this.value = value;
			this.returnType = Double.class;
			this.defaultValue = 0.0;
			this.preventNegative = true;
			this.throwExceptionOnError = false;
			this.customProperties = new HashMap<>();
		}
		
		// Getter 和 Setter 方法
		public String getOperationId() { return operationId; }
		public String getTableName() { return tableName; }
		public String getWhereClause() { return whereClause; }
		public String[] getWhereArgs() { return whereArgs; }
		public String getFieldName() { return fieldName; }
		public OperationType getOperationType() { return operationType; }
		public double getValue() { return value; }
		public Class<?> getReturnType() { return returnType; }
		public Object getDefaultValue() { return defaultValue; }
		public boolean isPreventNegative() { return preventNegative; }
		public boolean isThrowExceptionOnError() { return throwExceptionOnError; }
		public Map<String, Object> getCustomProperties() { return customProperties; }
		
		public void setReturnType(Class<?> returnType) { this.returnType = returnType; }
		public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
		public void setPreventNegative(boolean preventNegative) { this.preventNegative = preventNegative; }
		public void setThrowExceptionOnError(boolean throwExceptionOnError) { this.throwExceptionOnError = throwExceptionOnError; }
		public void setCustomProperty(String key, Object value) { this.customProperties.put(key, value); }
	}
	
	/**
	* 操作结果封装
	*/
	class OperationResult {
		private boolean success;
		private String message;
		private Object data;
		
		public OperationResult(boolean success, String message) {
			this.success = success;
			this.message = message;
		}
		
		public OperationResult(boolean success, String message, Object data) {
			this.success = success;
			this.message = message;
			this.data = data;
		}
		
		// Getters and setters
		public boolean isSuccess() { return success; }
		public String getMessage() { return message; }
		public Object getData() { return data; }
	}
	
	
	
}




/*
// 创建属性管理器
PropertyManager propManager = new PropertyManager(dbManager, "your_encryption_key");

// 批量操作示例
public void executeBatchOperations() {
// 1. 创建批量操作列表
List<PropertyManager.NumericOperation> operations = new ArrayList<>();

// 操作1：增加灵石数量
operations.add(new PropertyManager.NumericOperation(
"op1", "基础属性", "角色ID = ?", new String[]{"player123"},
"灵石", PropertyManager.NumericOperation.OperationType.INCREMENT, 100
));

// 操作2：减少仙石数量（防负值）
operations.add(new PropertyManager.NumericOperation(
"op2", "基础属性", "角色ID = ?", new String[]{"player123"},
"仙石", PropertyManager.NumericOperation.OperationType.DECREMENT, 50
));

// 操作3：设置等级
operations.add(new PropertyManager.NumericOperation(
"op3", "基础属性", "角色ID = ?", new String[]{"player123"},
"等级", PropertyManager.NumericOperation.OperationType.SET, 15
));

// 操作4：计算最大攻击力
operations.add(new PropertyManager.NumericOperation(
"op4", "战斗属性", "角色ID = ?", new String[]{"player123"},
"攻击", PropertyManager.NumericOperation.OperationType.MAX, 150
));

// 2. 执行批量操作
Map<String, Object> results = propManager.batchNumericOperations("基础属性", operations);

// 3. 处理结果
for (Map.Entry<String, Object> entry : results.entrySet()) {
String operationId = entry.getKey();
Object result = entry.getValue();

if (result instanceof PropertyManager.OperationResult) {
PropertyManager.OperationResult opResult = (PropertyManager.OperationResult) result;
if (opResult.isSuccess()) {
System.out.println("操作 " + operationId + " 成功: " + opResult.getData());
} else {
System.out.println("操作 " + operationId + " 失败: " + opResult.getMessage());
}
} else {
System.out.println("操作 " + operationId + " 结果: " + result);
}
}
}







*/