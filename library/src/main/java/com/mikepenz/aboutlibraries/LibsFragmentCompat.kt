package com.mikepenz.aboutlibraries

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.item.HeaderItem
import com.mikepenz.aboutlibraries.ui.item.LibraryItem
import com.mikepenz.aboutlibraries.ui.item.LoaderItem
import com.mikepenz.aboutlibraries.util.extractBooleanBundleOrResource
import com.mikepenz.aboutlibraries.util.extractStringBundleOrResource
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IItem
import com.mikepenz.fastadapter.adapters.ItemAdapter
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by mikepenz on 02.11.15.
 */
/**
 * Default Constructor
 * Gets an libs instance and gets all external libs
 */
class LibsFragmentCompat {
    private lateinit var mAdapter: FastAdapter<IItem<*>>
    private lateinit var mItemAdapter: ItemAdapter<IItem<*>>

    private lateinit var builder: LibsBuilder
    private var libraries: ArrayList<Library> = ArrayList()
    private var comparator: Comparator<Library>? = null
    private var mLibTask: LibraryTask? = null

    fun setLibraryComparator(comparator: Comparator<Library>) {
        this.comparator = comparator
    }

    fun onCreateView(context: Context, inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?, arguments: Bundle?): View {
        if (arguments != null) {
            builder = arguments.getSerializable("data") as LibsBuilder
        } else {
            Log.e("AboutLibraries", "The AboutLibraries fragment can't be build without the bundle containing the LibsBuilder")
            return View(context)
        }

        var view = inflater.inflate(R.layout.fragment_opensource, container, false)

        //allows to modify the view before creating
        view = LibsConfiguration.instance.uiListener?.preOnCreateView(view) ?: view

        // init CardView
        val mRecyclerView: RecyclerView
        mRecyclerView = if (view.id == R.id.cardListView) {
            view as RecyclerView
        } else {
            view.findViewById(R.id.cardListView) as RecyclerView
        }
        mRecyclerView.layoutManager = LinearLayoutManager(context)

        if (LibsConfiguration.instance.itemAnimator != null) {
            mRecyclerView.itemAnimator = LibsConfiguration.instance.itemAnimator
        } else {
            mRecyclerView.itemAnimator = DefaultItemAnimator()
        }

        mItemAdapter = ItemAdapter()
        mAdapter = FastAdapter.with<IItem<*>, ItemAdapter<IItem<*>>>(mItemAdapter)
        mRecyclerView.adapter = mAdapter

        if (builder.showLoadingProgress) {
            mItemAdapter.add(LoaderItem())
        }

        //allows to modify the view after creating
        view = LibsConfiguration.instance.uiListener?.postOnCreateView(view) ?: view

        return view
    }

    fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //load the data (only possible if we were able to get the Arguments
        if (view.context != null) {
            //fill the fragment with the content
            mLibTask = LibraryTask(view.context.applicationContext)
            executeLibTask(mLibTask)
        }
    }

    protected fun executeLibTask(libraryTask: LibraryTask?) {
        if (libraryTask != null) {
            when (builder.libTaskExecutor) {
                LibTaskExecutor.THREAD_POOL_EXECUTOR -> libraryTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                LibTaskExecutor.SERIAL_EXECUTOR -> libraryTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                LibTaskExecutor.DEFAULT_EXECUTOR -> libraryTask.execute()
            }
        }
    }


    fun onDestroyView() {
        if (mLibTask != null) {
            mLibTask?.cancel(true)
            mLibTask = null
        }
    }

    inner class LibraryTask(var ctx: Context) : AsyncTask<String, Unit, Unit>() {

        private var versionName: String? = null
        private var versionCode: Int? = null
        internal var icon: Drawable? = null

        override fun onPreExecute() {
            //started loading
            LibsConfiguration.instance.libTaskCallback?.onLibTaskStarted()
        }

        override fun doInBackground(vararg strings: String) {
            //init the Libs instance with fields if they were set
            val libs: Libs = if (builder.fields.isEmpty()) {
                Libs(ctx)
            } else {
                Libs(ctx, builder.fields)
            }

            //fill the builder with the information
            builder.aboutShowIcon = ctx.extractBooleanBundleOrResource(builder.aboutShowIcon, "aboutLibraries_description_showIcon") ?: false
            builder.aboutShowVersion = ctx.extractBooleanBundleOrResource(builder.aboutShowVersion, "aboutLibraries_description_showVersion") ?: false
            builder.aboutShowVersionName = ctx.extractBooleanBundleOrResource(builder.aboutShowVersionName, "aboutLibraries_description_showVersionName")
                    ?: false
            builder.aboutShowVersionCode = ctx.extractBooleanBundleOrResource(builder.aboutShowVersionCode, "aboutLibraries_description_showVersionCode")
                    ?: false

            builder.aboutAppName = ctx.extractStringBundleOrResource(builder.aboutAppName, "aboutLibraries_description_name") ?: ""
            builder.aboutDescription = ctx.extractStringBundleOrResource(builder.aboutDescription, "aboutLibraries_description_text") ?: ""

            builder.aboutAppSpecial1 = ctx.extractStringBundleOrResource(builder.aboutAppSpecial1, "aboutLibraries_description_special1_name")
            builder.aboutAppSpecial1Description = ctx.extractStringBundleOrResource(builder.aboutAppSpecial1Description, "aboutLibraries_description_special1_text")
            builder.aboutAppSpecial2 = ctx.extractStringBundleOrResource(builder.aboutAppSpecial2, "aboutLibraries_description_special2_name")
            builder.aboutAppSpecial2Description = ctx.extractStringBundleOrResource(builder.aboutAppSpecial2Description, "aboutLibraries_description_special2_text")
            builder.aboutAppSpecial3 = ctx.extractStringBundleOrResource(builder.aboutAppSpecial3, "aboutLibraries_description_special3_name")
            builder.aboutAppSpecial3Description = ctx.extractStringBundleOrResource(builder.aboutAppSpecial3Description, "aboutLibraries_description_special3_text")

            //apply modifications
            libs.modifyLibraries(builder.libraryModification)

            //fetch the libraries and sort if a comparator was set
            val doDefaultSort = builder.sort && null == builder.libraryComparator && null == comparator

            libraries = libs.prepareLibraries(ctx, builder.internalLibraries, builder.excludeLibraries, builder.autoDetect, builder.checkCachedDetection, doDefaultSort)

            if (comparator != null) {
                Collections.sort(libraries, comparator)
            } else if (builder.libraryComparator != null) {
                Collections.sort(libraries, builder.libraryComparator)
            }

            //load the data for the header
            val showVersionInfo = builder.aboutShowVersion || builder.aboutShowVersionName || builder.aboutShowVersionCode
            if (builder.aboutShowIcon && showVersionInfo) {
                //get the packageManager to load and read some values :D
                val pm = ctx.packageManager
                //get the packageName
                val packageName = ctx.packageName
                //Try to load the applicationInfo
                var appInfo: ApplicationInfo? = null
                var packageInfo: PackageInfo? = null
                try {
                    appInfo = pm.getApplicationInfo(packageName, 0)
                    packageInfo = pm.getPackageInfo(packageName, 0)
                } catch (ignored: Exception) {
                    // ignored
                }

                //Set the Icon or hide it
                if (builder.aboutShowIcon && appInfo != null) {
                    icon = appInfo.loadIcon(pm)
                }

                //set the Version or hide it
                versionName = null
                versionCode = null
                if (packageInfo != null) {
                    versionName = packageInfo.versionName
                    versionCode = packageInfo.versionCode
                }
            }
        }

        override fun onPostExecute(nothing: Unit) {
            //remove loader
            mItemAdapter.clear()

            //Add the header
            val showVersionInfo = builder.aboutShowVersion || builder.aboutShowVersionName || builder.aboutShowVersionCode
            if (builder.aboutShowIcon && showVersionInfo) {
                //add this cool thing to the headerView of our listView
                mItemAdapter.add(HeaderItem(builder).withAboutVersionName(versionName).withAboutVersionCode(versionCode).withAboutIcon(icon))
            }

            //add the libs
            val libraryItems = ArrayList<IItem<*>>()
            for (library in libraries) {
                libraryItems.add(LibraryItem(library, builder))
            }
            mItemAdapter.add(libraryItems)

            super.onPostExecute(nothing)

            //finished loading
            LibsConfiguration.instance.libTaskCallback?.onLibTaskFinished(mItemAdapter)
        }
    }
}
