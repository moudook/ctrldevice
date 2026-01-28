package com.ctrldevice.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Base template for a Clean Architecture UseCase.
 * Encapsulates a single business logic action.
 */
abstract class BaseUseCase<in P, out R> {

    /**
     * Executes the logic.
     * @param params Input parameters.
     * @return Result<R> containing Success or Failure.
     */
    suspend operator fun invoke(params: P): Result<R> {
        return try {
            val result = execute(params)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected abstract suspend fun execute(params: P): R
}
