package com.example.cubemaster.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cubemaster.data.local.dao.CompanyProfileDao
import com.example.cubemaster.data.local.entity.CompanyProfileEntity
import com.example.cubemaster.data.remote.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val companyName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val firebaseEmail: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDao: CompanyProfileDao,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(
        firebaseEmail = auth.currentUser?.email
    ))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileDao.observe().filterNotNull().collect { profile ->
                _state.update { s ->
                    s.copy(
                        companyName = profile.name,
                        address = profile.address ?: "",
                        phone = profile.phone ?: "",
                        email = profile.email ?: ""
                    )
                }
            }
        }
    }

    fun setCompanyName(v: String) = _state.update { it.copy(companyName = v) }
    fun setAddress(v: String) = _state.update { it.copy(address = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setEmail(v: String) = _state.update { it.copy(email = v) }

    fun save() {
        viewModelScope.launch {
            profileDao.upsert(
                CompanyProfileEntity(
                    name = _state.value.companyName,
                    address = _state.value.address.takeIf { it.isNotBlank() },
                    phone = _state.value.phone.takeIf { it.isNotBlank() },
                    email = _state.value.email.takeIf { it.isNotBlank() },
                    logoUrl = null
                )
            )
        }
    }

    fun signOut() = auth.signOut()
}
