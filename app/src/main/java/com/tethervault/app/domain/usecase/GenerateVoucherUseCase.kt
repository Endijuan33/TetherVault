package com.tethervault.app.domain.usecase

import com.tethervault.app.domain.model.Voucher
import com.tethervault.app.domain.repository.VoucherRepository
import java.security.SecureRandom
import javax.inject.Inject

class GenerateVoucherUseCase @Inject constructor(
    private val voucherRepository: VoucherRepository
) {

    suspend operator fun invoke(durationHours: Int): Voucher {
        val now = System.currentTimeMillis()
        val voucher = Voucher(
            code = generateCode(),
            createdAt = now,
            expiresAt = now + durationHours * MILLIS_PER_HOUR
        )
        voucherRepository.upsert(voucher)
        return voucher
    }

    private fun generateCode(): String {
        val alphabet = CODE_ALPHABET.toCharArray()
        val random = SecureRandom()
        return buildString {
            repeat(CODE_LENGTH) {
                append(alphabet[random.nextInt(alphabet.size)])
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 8
        const val MILLIS_PER_HOUR = 3_600_000L

        // Ambiguous characters (I, O, 0, 1) are excluded for readability.
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
