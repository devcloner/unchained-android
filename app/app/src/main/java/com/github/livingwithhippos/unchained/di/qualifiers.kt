package com.github.livingwithhippos.unchained.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApiRetrofit

/** Debrid-Link API v2 (a different host and error envelope than Real-Debrid). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DebridLinkRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TorrentNotification

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TorrentSummaryNotification

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ClassicClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DOHClient
