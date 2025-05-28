package com.example.gammaplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity(), VideoAdapter.OnItemClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var currentDirectory: File? = null
    private val folderOrVideoList = mutableListOf<File>()
    private var isRoot = true
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val TAG = "GammaPlayer"

    private val gson = Gson()
    private var folderCache = mutableMapOf<String, List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = VideoAdapter(folderOrVideoList, this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(TopBorderItemDecoration(this))

        swipeRefreshLayout.setOnRefreshListener {
            refreshCurrentFolder()
        }

        loadCacheFromDisk()
        checkPermissions()
        refreshCurrentFolder()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
            } else {
                loadRootStorages()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                loadRootStorages()
            }
        } else {
            loadRootStorages()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadRootStorages()
        } else {
            Toast.makeText(this, "Permission required to access videos", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getAllStoragePaths(): List<File> {
        val storagePaths = mutableListOf<File>()
        val storageManager = getSystemService(STORAGE_SERVICE) as StorageManager
        val storageVolumeList = storageManager.storageVolumes

        if (storageVolumeList.isNotEmpty()) {
            storagePaths.add(Environment.getExternalStorageDirectory())
            Log.d(TAG, "Internal storage added: ${Environment.getExternalStorageDirectory().absolutePath}")
        }

        if (storageVolumeList.size > 1) {
            try {
                val sdCardPath = getExternalFilesDirs(null)[1]
                if (sdCardPath != null && sdCardPath.exists() && sdCardPath.canRead()) {
                    val rootSdCardPath = sdCardPath.parentFile?.parentFile?.parentFile?.parentFile
                    if (rootSdCardPath != null && rootSdCardPath.exists() && rootSdCardPath.canRead()) {
                        storagePaths.add(rootSdCardPath)
                        Log.d(TAG, "SD Card storage added: ${rootSdCardPath.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SD Card detection failed: ${e.localizedMessage}")
            }
        }

        Log.d(TAG, "Total storage paths: ${storagePaths.size}")
        return storagePaths
    }

    private fun loadRootStorages() {
        folderOrVideoList.clear()
        adapter.notifyDataSetChanged()
        isRoot = true
        currentDirectory = null

        val storages = getAllStoragePaths()
        folderOrVideoList.addAll(storages)
        adapter.notifyDataSetChanged()
    }

    private fun loadFolder(directory: File, forceRefresh: Boolean = false) {
        Log.d(TAG, "Loading folder: ${directory.absolutePath}")
        folderOrVideoList.clear()
        adapter.notifyDataSetChanged()
        isRoot = false
        currentDirectory = directory

        if (!forceRefresh) {
            val cachedList = folderCache[directory.absolutePath]
            if (cachedList != null) {
                Log.d(TAG, "Cache hit for ${directory.absolutePath}")
                cachedList.map { File(it) }.filter { it.exists() }.forEach { folderOrVideoList.add(it) }
                adapter.notifyDataSetChanged()
                return
            }
        }

        Log.d(TAG, "Scanning ${directory.absolutePath}...")
        mainScope.launch {
            val tempList = mutableListOf<File>()

            withContext(Dispatchers.IO) {
                val folders = mutableListOf<File>()
                val videos = mutableListOf<File>()

                val files = directory.listFiles()
                Log.d(TAG, "Found ${files?.size ?: 0} items in ${directory.name}")

                files?.filterNotNull()?.forEach {
                    if (it.isDirectory && hasVideoFiles(it)) {
                        folders.add(it)
                    } else if (it.isFile && isVideoFile(it)) {
                        videos.add(it)
                    }
                }

                tempList.addAll(folders)
                tempList.addAll(videos)
            }

            folderOrVideoList.addAll(tempList)
            adapter.notifyDataSetChanged()
            folderCache[directory.absolutePath] = tempList.map { it.absolutePath }
            saveCacheToDisk()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun refreshCurrentFolder() {
        if (isRoot) {
            loadRootStorages()
            swipeRefreshLayout.isRefreshing = false
        } else {
            loadFolder(currentDirectory ?: return, forceRefresh = true)
        }
    }

    private fun hasVideoFiles(folder: File): Boolean {
        val files = folder.listFiles()
        files?.forEach {
            if (it.isFile && isVideoFile(it)) {
                return true
            } else if (it.isDirectory) {
                if (hasVideoFiles(it)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isVideoFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
                name.endsWith(".webm") || name.endsWith(".flv")
    }

    override fun onItemClick(file: File) {
        if (file.isDirectory) {
            loadFolder(file)
        } else {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("videoPath", file.absolutePath)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (!isRoot) {
            val parent = currentDirectory?.parentFile
            if (parent == null || parent.absolutePath == "/storage" || currentDirectory?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                loadRootStorages()
            } else {
                loadFolder(parent, forceRefresh = true)
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private fun saveCacheToDisk() {
        try {
            val json = gson.toJson(folderCache)
            val file = File(filesDir, "folderCache.json")
            file.writeText(json)
            Log.d(TAG, "Cache saved to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache: ${e.localizedMessage}")
        }
    }

    private fun loadCacheFromDisk() {
        try {
            val file = File(filesDir, "folderCache.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<MutableMap<String, List<String>>>() {}.type
                folderCache = gson.fromJson(json, type)
                Log.d(TAG, "Cache loaded from disk")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache: ${e.localizedMessage}")
        }
    }
}
