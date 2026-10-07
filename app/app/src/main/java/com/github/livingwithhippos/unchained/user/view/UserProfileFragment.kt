package com.github.livingwithhippos.unchained.user.view

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import coil.load
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.User
import com.github.livingwithhippos.unchained.data.repository.DebridLinkRepository
import com.github.livingwithhippos.unchained.databinding.FragmentUserProfileBinding
import com.github.livingwithhippos.unchained.settings.view.SettingsActivity
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_ASKED
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_USE
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import com.github.livingwithhippos.unchained.utilities.ACCOUNT_LINK
import com.github.livingwithhippos.unchained.utilities.DEBRID_PROVIDER_PREF_KEY
import com.github.livingwithhippos.unchained.utilities.DebridProvider
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.REFERRAL_LINK
import com.github.livingwithhippos.unchained.utilities.extension.openExternalWebPage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A simple [UnchainedFragment] subclass. Shows a user profile details. */
@AndroidEntryPoint
class UserProfileFragment : UnchainedFragment() {

    @Inject
    lateinit var debridLinkRepository: DebridLinkRepository

    @Inject lateinit var preferences: SharedPreferences

    private var _binding: FragmentUserProfileBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        val view = binding.root

        val isDebridLink =
            preferences.getString(DEBRID_PROVIDER_PREF_KEY, DebridProvider.REAL_DEBRID.id) ==
                DebridProvider.DEBRID_LINK.id || debridLinkRepository.isConfigured()

        if (isDebridLink && debridLinkRepository.isConfigured()) {
            binding.tvDescription.text = getString(R.string.auth_with_debrid_link)
            binding.tvLoginDescription.text = getString(R.string.auth_with_debrid_link)
            binding.bAccount.text = "Debrid-Link"
            binding.bAccount.setOnClickListener {
                context?.openExternalWebPage("https://debrid-link.com/webapp/seedbox")
            }
            lifecycleScope.launch {
                when (val account = debridLinkRepository.getAccountInfos()) {
                    is EitherResult.Success -> {
                        val acc = account.success
                        binding.tvName.text = acc.username ?: "Debrid-Link User"
                        binding.tvMail.text = acc.email ?: ""
                        val isPremium = (acc.accountType ?: 0) >= 1 || (acc.premiumLeft ?: 0L) > 0L
                        if (isPremium) {
                            binding.tvPremium.text = getString(R.string.premium)
                        } else {
                            binding.tvPremium.text = getString(R.string.not_premium)
                        }
                        val days = ((acc.premiumLeft ?: 0L) / 86400L).coerceAtLeast(0L)
                        val pts = (acc.points ?: 0L).toInt()
                        binding.tvPremiumDays.text = getString(R.string.premium_days_format, days)
                        binding.tvPoints.text = getString(R.string.premium_points_format, pts)
                        binding.pointsBar.setProgressCompat(pts.coerceIn(0, 1000), true)
                    }
                    is EitherResult.Failure -> {
                        binding.tvName.text = "Debrid-Link"
                        binding.tvPremium.text = getString(R.string.debrid_link_api_key_title)
                    }
                }
            }
        } else {
            val user: User? = activityViewModel.getCachedUser()
            if (user == null) {
                activityViewModel.fetchUser()
            } else {
                populateUserView(user)
            }
            lifecycleScope.launch {
                if (activityViewModel.isTokenPrivate()) {
                    if (_binding == null) return@launch
                    binding.tvLoginDescription.text = getString(R.string.login_type_private)
                } else {
                    if (_binding == null) return@launch
                    binding.tvLoginDescription.text = getString(R.string.login_type_open)
                }
            }
        }

        activityViewModel.userLiveData.observe(viewLifecycleOwner) {
            if (_binding == null) return@observe
            populateUserView(it.peekContent())
            lifecycleScope.launch {
                if (activityViewModel.isTokenPrivate()) {
                    binding.tvLoginDescription.text = getString(R.string.login_type_private)
                } else {
                    binding.tvLoginDescription.text = getString(R.string.login_type_open)
                }
            }
        }

        binding.bAccount.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            // if we never asked, show a dialog
            if (!preferences.getBoolean(KEY_REFERRAL_ASKED, false)) {
                // set asked as true
                preferences.edit { putBoolean(KEY_REFERRAL_ASKED, true) }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.referral))
                    .setMessage(getString(R.string.referral_proposal))
                    .setNegativeButton(getString(R.string.decline)) { _, _ ->
                        preferences.edit { putBoolean(KEY_REFERRAL_USE, false) }
                        context?.openExternalWebPage(ACCOUNT_LINK)
                    }
                    .setPositiveButton(getString(R.string.accept)) { _, _ ->
                        preferences.edit { putBoolean(KEY_REFERRAL_USE, true) }
                        context?.openExternalWebPage(REFERRAL_LINK)
                    }
                    .show()
            } else {
                if (preferences.getBoolean(KEY_REFERRAL_USE, false))
                    context?.openExternalWebPage(REFERRAL_LINK)
                else context?.openExternalWebPage(ACCOUNT_LINK)
            }
        }

        activityViewModel.fsmAuthenticationState.observe(viewLifecycleOwner) {
            if (it != null) {
                when (it.peekContent()) {
                    is FSMAuthenticationState.WaitingUserAction -> {
                        // an error occurred, check it and eventually go back to the start fragment
                        val action = UserProfileFragmentDirections.actionUserToStartFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.StartNewLogin -> {
                        // the user reset the login, go to the auth fragment
                        val action =
                            UserProfileFragmentDirections.actionUserToAuthenticationFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.AuthenticatedOpenToken,
                    FSMAuthenticationState.AuthenticatedPrivateToken,
                    FSMAuthenticationState.RefreshingOpenToken -> {
                        // managed by activity
                    }

                    FSMAuthenticationState.CheckCredentials -> {
                        // shouldn't matter
                    }

                    FSMAuthenticationState.Start,
                    FSMAuthenticationState.WaitingToken,
                    FSMAuthenticationState.WaitingUserConfirmation -> {
                        // shouldn't happen
                    }
                }
            }
        }

        binding.bSettings.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            activityViewModel.requireNotificationPermissions()
        }

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            activityViewModel.requireLocalNetworkPermissions()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun populateUserView(user: User?) {
        if (_binding == null) return
        user?.let {
            binding.tvName.text = it.username
            binding.tvMail.text = it.email
            // todo: check https://coil-kt.github.io/coil/image_loaders/#caching
            binding.ivProfilePic.load(it.avatar) { crossfade(true) }
            if (it.premium > 0) {
                binding.tvPremium.text = getString(R.string.premium)
            } else {
                binding.tvPremium.text = getString(R.string.not_premium)
            }
            binding.tvPremiumDays.text =
                getString(R.string.premium_days_format, it.premium / 60 / 60 / 24)
            binding.tvPoints.text = getString(R.string.premium_points_format, it.points)
            binding.pointsBar.setProgressCompat(it.points, true)
        }
    }
}
