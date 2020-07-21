package com.example.rocketreserver

import android.content.Context
import android.os.Looper
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

private var instance: ApolloClient? = null

fun apolloClient(context: Context): ApolloClient {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "Only the main thread can get the apolloClient instance"
    }

    if (instance != null) {
        return instance!!
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthorizationInterceptor(context))
        .build()

    val sqlNormalizedCacheFactory = SqlNormalizedCacheFactory(context, "apollo.db")
    val resolver: CacheKeyResolver = object : CacheKeyResolver() {
        override fun fromFieldRecordSet(
            field: ResponseField,
            recordSet: Map<String, Any>
        ): CacheKey {
            val id = recordSet["id"] as String?
            return if (id != null) CacheKey.from(id) else CacheKey.NO_KEY
        }

        override fun fromFieldArguments(
            field: ResponseField,
            variables: Operation.Variables
        ): CacheKey {
            val id = field.resolveArgument("id", variables) as String?
            return if (id != null) CacheKey.from(id) else CacheKey.NO_KEY
        }
    }

    instance = ApolloClient.builder()
        .serverUrl("https://apollo-fullstack-tutorial.herokuapp.com/graphql")
        .subscriptionTransportFactory(
            WebSocketSubscriptionTransport.Factory(
                "wss://apollo-fullstack-tutorial.herokuapp.com/graphql",
                okHttpClient
            )
        )
        .normalizedCache(sqlNormalizedCacheFactory, resolver)
        .okHttpClient(okHttpClient)
        .build()

    return instance!!
}

private class AuthorizationInterceptor(val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", User.getToken(context) ?: "")
            .build()

        return chain.proceed(request)
    }
}
