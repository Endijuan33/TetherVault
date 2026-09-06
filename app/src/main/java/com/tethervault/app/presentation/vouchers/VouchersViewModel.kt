package com.tethervault.app.presentation.vouchers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tethervault.app.domain.model.Voucher
import com.tethervault.app.domain.repository.VoucherRepository
import com.tethervault.app.domain.usecase.GenerateVoucherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VouchersViewModel @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val generateVoucherUseCase: GenerateVoucherUseCase
) : ViewModel() {

    val vouchers: StateFlow<List<Voucher>> = voucherRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun generateVoucher(durationHours: Int) {
        viewModelScope.launch {
            generateVoucherUseCase(durationHours)
        }
    }

    fun deleteVoucher(voucher: Voucher) {
        viewModelScope.launch {
            voucherRepository.deleteById(voucher.id)
        }
    }
}
