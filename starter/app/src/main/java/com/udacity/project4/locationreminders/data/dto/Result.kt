package com.udacity.project4.locationreminders.data.dto

import java.lang.Exception


/**
 * A sealed class that encapsulates successful outcome with a value of type [T]
 * or a failure with message and statusCode
 */
sealed class Result<out T : Any> {
    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Error(val message: Exception, val statusCode: Int? = null) :
        Result<Nothing>()
}