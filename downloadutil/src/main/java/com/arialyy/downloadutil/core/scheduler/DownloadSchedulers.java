/*
 * Copyright (C) 2016 AriaLyy(DownloadUtil)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arialyy.downloadutil.core.scheduler;

import android.content.Context;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.arialyy.downloadutil.core.DownloadEntity;
import com.arialyy.downloadutil.core.queue.ITaskQueue;
import com.arialyy.downloadutil.core.task.Task;
import com.arialyy.downloadutil.core.queue.pool.ExecutePool;
import com.arialyy.downloadutil.core.queue.DownloadTaskQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by lyy on 2016/8/16.
 * 任务下载器，提供抽象的方法供具体的实现类操作
 */
public class DownloadSchedulers implements IDownloadSchedulers {
  /**
   * 任务开始
   */
  public static final int START = 1;
  /**
   * 任务停止
   */
  public static final int STOP = 2;
  /**
   * 任务失败
   */
  public static final int FAIL = 3;
  /**
   * 任务取消
   */
  public static final int CANCEL = 4;
  /**
   * 任务完成
   */
  public static final int COMPLETE = 5;
  /**
   * 下载中
   */
  public static final int RUNNING = 6;
  private static final String TAG = "DownloadSchedulers";
  private static final Object LOCK = new Object();
  private static volatile DownloadSchedulers INSTANCE = null;
  /**
   * 下载失败次数
   */
  int mFailNum = 10;

  /**
   * 超时时间
   */
  long mTimeOut = 10000;

  /**
   * 下载器任务监听
   */
  OnSchedulerListener mSchedulerListener;
  Map<Integer, OnSchedulerListener> mSchedulerListeners = new HashMap<>();
  ITaskQueue mQueue;

  public DownloadSchedulers(ITaskQueue downloadTaskQueue) {
    mQueue = downloadTaskQueue;
  }

  public static DownloadSchedulers getInstance(DownloadTaskQueue queue) {
    if (INSTANCE == null) {
      synchronized (LOCK) {
        INSTANCE = new DownloadSchedulers(queue);
      }
    }
    return INSTANCE;
  }

  @Override public boolean handleMessage(Message msg) {
    DownloadEntity entity = (DownloadEntity) msg.obj;
    if (entity == null) {
      Log.e(TAG, "请传入下载实体DownloadEntity");
      return true;
    }
    switch (msg.what) {
      case STOP:
      case CANCEL:
        if (mQueue.size() != ExecutePool.mSize) {
          startNextTask(entity);
        }
        break;
      case COMPLETE:
        startNextTask(entity);
        break;
      case FAIL:
        handleFailTask(entity);
        break;
    }
    callback(msg.what, entity);
    return true;
  }

  /**
   * 回调
   *
   * @param state 状态
   * @param entity 下载实体
   */
  private void callback(int state, DownloadEntity entity) {
    if (mSchedulerListeners.size() > 0) {
      //Set<Map.Entry<Integer, String>>
      for (Map.Entry<Integer, OnSchedulerListener> entry : mSchedulerListeners.entrySet()) {
        callback(state, entity, entry.getValue());
      }
    }
  }

  private void callback(int state, DownloadEntity entity, OnSchedulerListener listener) {
    if (listener != null) {
      Task task = mQueue.getTask(entity);
      switch (state) {
        case RUNNING:
          listener.onTaskRunning(task);
          break;
        case START:
          listener.onTaskStart(task);
          break;
        case STOP:
          listener.onTaskStop(task);
          break;
        case CANCEL:
          listener.onTaskCancel(task);
          removeSchedulerListener(listener);
          break;
        case COMPLETE:
          listener.onTaskComplete(task);
          removeSchedulerListener(listener);
          break;
        case FAIL:
          listener.onTaskFail(task);
          removeSchedulerListener(listener);
          break;
      }
    }
  }

  /**
   * 处理下载任务下载失败的情形
   *
   * @param entity 失败实体
   */
  @Override public void handleFailTask(DownloadEntity entity) {
    if (entity.getFailNum() <= mFailNum) {
      Task task = mQueue.getTask(entity);
      mQueue.reTryStart(task);
    } else {
      startNextTask(entity);
    }
  }

  /**
   * 启动下一个任务，条件：任务停止，取消下载，任务完成
   *
   * @param entity 通过Handler传递的下载实体
   */
  @Override public void startNextTask(DownloadEntity entity) {
    mQueue.removeTask(entity);
    Task newTask = mQueue.getNextTask();
    if (newTask == null) {
      Log.w(TAG, "没有下一任务");
      return;
    }
    if (newTask.getDownloadEntity().getState() == DownloadEntity.STATE_WAIT) {
      mQueue.startTask(newTask);
    }
  }

  @Override public void addSchedulerListener(Context context, OnSchedulerListener schedulerListener) {
    mSchedulerListeners.put(schedulerListener.hashCode(), schedulerListener);
  }

  @Override public void removeSchedulerListener(OnSchedulerListener schedulerListener) {
    mSchedulerListeners.remove(schedulerListener.hashCode());
  }

  public void setFailNum(int mFailNum) {
    this.mFailNum = mFailNum;
  }

  public void setTimeOut(long timeOut) {
    this.mTimeOut = timeOut;
  }
}