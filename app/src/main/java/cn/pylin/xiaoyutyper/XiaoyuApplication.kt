package cn.pylin.xiaoyutyper

import android.app.Application
import cn.pylin.xiaoyutyper.pinyin.PinyinRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XiaoyuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            PinyinRepository.get(this@XiaoyuApplication).engine()
        }
    }
}
