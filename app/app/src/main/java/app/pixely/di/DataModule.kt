package app.pixely.di

import android.content.Context
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.search.KotlinScanIndex
import app.pixely.data.search.SqliteVecIndex
import app.pixely.data.search.VectorIndex
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun opened(@ApplicationContext context: Context): PixelyDatabase.Opened = PixelyDatabase.open(context)

    @Provides
    fun database(opened: PixelyDatabase.Opened): PixelyDatabase = opened.db

    @Provides
    @Singleton
    fun vectorIndex(opened: PixelyDatabase.Opened): VectorIndex =
        if (opened.vecLoaded) SqliteVecIndex(opened.db) else KotlinScanIndex(opened.db)
}
