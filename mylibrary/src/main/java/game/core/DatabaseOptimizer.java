package game.core;

import android.util.Log;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import java.io.File;
/**
* 数据库优化管理器 - 提供专业的数据库性能优化和维护功能
* 设计模式：采用 TableManager 风格，与 DBCipherManager 紧密集成
*/
public class DatabaseOptimizer {
	private static final String TAG = "DatabaseOptimizer";
	private final DBCipherManager dbManager;
	
	public DatabaseOptimizer(DBCipherManager dbManager) {
		this.dbManager = dbManager;
	}
	
	/**
	* 执行全面的数据库性能优化
	* 包括：自动优化、缓存调整、页面大小设置等
	* @return true优化成功，false优化失败
	*/
	public boolean optimizeDatabase() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始执行数据库全面优化", null);
			AtomicBoolean success = new AtomicBoolean(true);
			
			try {
				// 1. 启用WAL模式（如果支持）
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
					if (!enableWALMode(db)) {
						log(DBCipherManager.LogLevel.WARN, "WAL模式启用失败", null);
						success.set(false);
					}
				}
				
				// 2. 调整缓存大小
				if (!optimizeCacheSize(db)) {
					log(DBCipherManager.LogLevel.WARN, "缓存大小调整失败", null);
					success.set(false);
				}
				
				// 4. 执行自动优化
				if (!autoOptimize(db)) {
					log(DBCipherManager.LogLevel.WARN, "自动优化执行失败", null);
					success.set(false);
				}
				
				// 5. 记录优化结果
				logOptimizationStats(db);
				
				if (success.get()) {
					log(DBCipherManager.LogLevel.INFO, "数据库全面优化完成", null);
				} else {
					log(DBCipherManager.LogLevel.WARN, "数据库优化部分失败", null);
				}
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "数据库优化过程中发生异常", e);
				success.set(false);
			}
			return success.get();
		});
	}
	
	/**
	* 执行数据库整理（VACUUM）- 需要谨慎使用
	* @return true整理成功，false整理失败
	*/
	public boolean vacuumDatabase() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始执行数据库整理(VACUUM)...", null);
			long startTime = System.currentTimeMillis();
			boolean success = false;
			
			try {
				db.execSQL("VACUUM;");
				long endTime = System.currentTimeMillis();
				log(DBCipherManager.LogLevel.INFO,
				String.format("数据库整理完成，耗时: %dms", (endTime - startTime)), null);
				success = true;
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "数据库整理失败", e);
			}
			return success;
		});
	}
	
	/**
	* 重建所有索引
	* @return true重建成功，false重建失败
	*/
	public boolean rebuildIndexes() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始重建所有索引", null);
			boolean success = true;
			Cursor cursor = null;
			
			try {
				// 获取所有表名
				cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
				if (cursor != null) {
					while (cursor.moveToNext()) {
						String tableName = cursor.getString(0);
						try {
							db.execSQL("REINDEX " + tableName);
							log(DBCipherManager.LogLevel.DEBUG, "重建索引: " + tableName, null);
						} catch (Exception e) {
							log(DBCipherManager.LogLevel.WARN, "重建索引失败: " + tableName, e);
							success = false;
						}
					}
				}
				
				if (success) {
					log(DBCipherManager.LogLevel.INFO, "所有索引重建完成", null);
				} else {
					log(DBCipherManager.LogLevel.WARN, "部分索引重建失败", null);
				}
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "重建索引过程中发生异常", e);
				success = false;
			} finally {
				if (cursor != null) cursor.close();
			}
			return success;
		});
	}
	
	/**
	* 检查数据库健康状态
	* @return 包含健康状态信息的JSONObject
	*/
	public JSONObject checkDatabaseHealth() {
		return dbManager.executeWithConnection(db -> {
			log(DBCipherManager.LogLevel.INFO, "开始数据库健康检查", null);
			JSONObject healthReport = new JSONObject();
			
			try {
				// 1. 完整性检查
				Cursor integrityCursor = db.rawQuery("PRAGMA integrity_check;", null);
				if (integrityCursor != null && integrityCursor.moveToFirst()) {
					String integrity = integrityCursor.getString(0);
					healthReport.put("integrity", integrity);
					log(DBCipherManager.LogLevel.INFO, "数据库完整性检查: " + integrity, null);
				}
				if (integrityCursor != null) integrityCursor.close();
				
				// 2. 空闲页检查
				Cursor freelistCursor = db.rawQuery("PRAGMA freelist_count;", null);
				if (freelistCursor != null && freelistCursor.moveToFirst()) {
					int freelistPages = freelistCursor.getInt(0);
					healthReport.put("freelist_pages", freelistPages);
					log(DBCipherManager.LogLevel.INFO, "空闲页数量: " + freelistPages, null);
				}
				if (freelistCursor != null) freelistCursor.close();
				
				// 3. 自动清理设置
				Cursor vacuumCursor = db.rawQuery("PRAGMA auto_vacuum;", null);
				if (vacuumCursor != null && vacuumCursor.moveToFirst()) {
					int autoVacuum = vacuumCursor.getInt(0);
					healthReport.put("auto_vacuum", autoVacuum);
					log(DBCipherManager.LogLevel.INFO, "自动清理设置: " + autoVacuum, null);
				}
				if (vacuumCursor != null) vacuumCursor.close();
				
				// 4. 数据库大小
				File dbFile = new File(db.getPath());
				healthReport.put("database_size", dbFile.length());
				log(DBCipherManager.LogLevel.INFO, "数据库大小: " + dbFile.length() + " 字节", null);
				
				// 5. 表统计
				JSONObject tablesJson = new JSONObject();
				Cursor tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
				if (tableCursor != null) {
					while (tableCursor.moveToNext()) {
						String tableName = tableCursor.getString(0);
						JSONObject tableInfo = new JSONObject();
						
						// 行数
						Cursor countCursor = db.rawQuery("SELECT count(*) FROM " + tableName, null);
						if (countCursor != null && countCursor.moveToFirst()) {
							int rowCount = countCursor.getInt(0);
							tableInfo.put("row_count", rowCount);
						}
						if (countCursor != null) countCursor.close();
						
						// 表大小
						Cursor sizeCursor = db.rawQuery("SELECT SUM(pgsize) FROM dbstat WHERE name=?",
						new String[]{tableName});
						if (sizeCursor != null && sizeCursor.moveToFirst()) {
							long tableSize = sizeCursor.getLong(0);
							tableInfo.put("table_size", tableSize);
						}
						if (sizeCursor != null) sizeCursor.close();
						
						tablesJson.put(tableName, tableInfo);
					}
				}
				if (tableCursor != null) tableCursor.close();
				
				healthReport.put("tables", tablesJson);
				log(DBCipherManager.LogLevel.INFO, "数据库健康检查完成", null);
				
			} catch (Exception e) {
				log(DBCipherManager.LogLevel.ERROR, "数据库健康检查失败", e);
			}
			return healthReport;
		});
	}
	
	// ==================== 内部优化方法 ====================
	
	/**
	* 启用WAL模式
	*/
	private boolean enableWALMode(SQLiteDatabase db) {
		try {
			Cursor cursor = db.rawQuery("PRAGMA journal_mode=WAL;", null);
			if (cursor != null && cursor.moveToFirst()) {
				String mode = cursor.getString(0);
				log(DBCipherManager.LogLevel.INFO, "WAL模式设置: " + mode, null);
				return "wal".equalsIgnoreCase(mode);
			}
			return false;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "WAL模式设置失败", e);
			return false;
		}
	}
	
	/**
	* 优化缓存大小
	*/
	private boolean optimizeCacheSize(SQLiteDatabase db) {
		try {
			// 根据设备内存动态调整缓存大小
			long maxMemory = Runtime.getRuntime().maxMemory();
			int cacheSize;
			
			if (maxMemory > 200 * 1024 * 1024) { // 200MB以上
				cacheSize = 10000;  // 约40MB缓存
			} else if (maxMemory > 100 * 1024 * 1024) { // 100MB以上
				cacheSize = 5000;   // 约20MB缓存
			} else {
				cacheSize = 2000;   // 约8MB缓存
			}
			
			db.execSQL("PRAGMA cache_size=" + cacheSize + ";");
			log(DBCipherManager.LogLevel.INFO, "设置缓存大小: " + cacheSize + " 页", null);
			return true;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "缓存大小优化失败", e);
			return false;
		}
	}
	
	/**
	* 设置页面大小（对新数据库有效）
	*/
	private boolean setPageSize(SQLiteDatabase db) {
		try {
			// 检查当前页面大小
			Cursor cursor = db.rawQuery("PRAGMA page_size;", null);
			if (cursor != null && cursor.moveToFirst()) {
				int currentSize = cursor.getInt(0);
				cursor.close();
				
				// 如果页面大小较小，尝试设置为4KB（最佳性能）
				if (currentSize < 4096) {
					db.execSQL("PRAGMA page_size=4096;");
					log(DBCipherManager.LogLevel.INFO, "设置页面大小为4KB", null);
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "页面大小设置失败", e);
			return false;
		}
	}
	
	/**
	* 执行自动优化
	*/
	private boolean autoOptimize(SQLiteDatabase db) {
		try {
			db.execSQL("PRAGMA optimize;");
			log(DBCipherManager.LogLevel.INFO, "自动优化执行完成", null);
			return true;
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.ERROR, "自动优化执行失败", e);
			return false;
		}
	}
	
	/**
	* 记录优化统计信息
	*/
	private void logOptimizationStats(SQLiteDatabase db) {
		try {
			// 获取数据库统计信息
			Cursor statsCursor = db.rawQuery("PRAGMA stats;", null);
			StringBuilder stats = new StringBuilder("数据库统计信息:\n");
			if (statsCursor != null) {
				while (statsCursor.moveToNext()) {
					stats.append(statsCursor.getString(0)).append(": ")
					.append(statsCursor.getString(1)).append("\n");
				}
				statsCursor.close();
			}
			log(DBCipherManager.LogLevel.DEBUG, stats.toString(), null);
		} catch (Exception e) {
			log(DBCipherManager.LogLevel.WARN, "统计信息获取失败", e);
		}
	}
	
	// ==================== 日志辅助方法 ====================
	
	private void log(DBCipherManager.LogLevel level, String message, Throwable throwable) {
		this.dbManager.log(level, TAG, message, throwable);
	}
}