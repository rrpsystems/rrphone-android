package com.rrpsystems.rrphone.core.api

import com.rrpsystems.rrphone.core.security.KeystoreManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente configurado do Retrofit para chamadas da API.
 */
object RetrofitClient {

    // TODO: Ajustar a URL para o servidor real de staging/produção
    private const val BASE_URL = "http://10.0.2.2:8000/"

    fun getService(keystoreManager: KeystoreManager): RRPAuthService {
        
        // Interceptor para logs (útil para debug no console)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Interceptor para adicionar o Header de Authorization (JWT)
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            // Tenta buscar o token do KeystoreManager
            val token = keystoreManager.getSecureString(KeystoreManager.KEY_AUTH_TOKEN)
            if (!token.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            // Define o formato de requisição padrão
            requestBuilder.addHeader("Accept", "application/json")

            chain.proceed(requestBuilder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(RRPAuthService::class.java)
    }
}
