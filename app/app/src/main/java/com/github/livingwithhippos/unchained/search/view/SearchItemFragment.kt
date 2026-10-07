package com.github.livingwithhippos.unchained.search.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.repository.DebridLinkRepository
import com.github.livingwithhippos.unchained.databinding.FragmentSearchItemBinding
import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.model.LinkItem
import com.github.livingwithhippos.unchained.search.model.LinkItemAdapter
import com.github.livingwithhippos.unchained.search.model.LinkItemListener
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.extension.copyToClipboard
import com.github.livingwithhippos.unchained.utilities.extension.openExternalWebPage
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchItemFragment : UnchainedFragment(), LinkItemListener {

    @Inject
    lateinit var debridLinkRepository: DebridLinkRepository

    private val args: SearchItemFragmentArgs by navArgs()

    private var _binding: FragmentSearchItemBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchItemBinding.inflate(inflater, container, false)

        setup(binding)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setup(binding: FragmentSearchItemBinding) {
        val item: ScrapedItem = args.item

        binding.tvName.text = item.name
        binding.size.text = item.size ?: "-"
        binding.seeders.text = item.seeders ?: "-"
        binding.leechers.text = item.leechers ?: "-"

        binding.linkCaption.setOnClickListener {
            if (item.link != null) context?.openExternalWebPage(item.link)
        }

        val adapter = LinkItemAdapter(this)
        binding.linkList.adapter = adapter

        val links = mutableListOf<LinkItem>()

        item.magnets.forEach {
            links.add(LinkItem(getString(R.string.magnet), it.substringBefore("&"), it))
        }
        item.torrents.forEach { links.add(LinkItem(getString(R.string.torrent), it, it)) }
        item.hosting.forEach { links.add(LinkItem(getString(R.string.hoster), it, it)) }
        adapter.submitList(links)
    }

    private fun sendToDebridLink(url: String) {
        if (!debridLinkRepository.isConfigured()) {
            context?.showToast(R.string.debrid_link_no_key)
            return
        }
        context?.showToast(R.string.loading_torrent_file)
        lifecycleScope.launch {
            when (debridLinkRepository.addMagnet(url)) {
                is EitherResult.Success -> {
                    context?.showToast(R.string.debrid_link_add_success)
                }
                is EitherResult.Failure -> {
                    context?.showToast(R.string.debrid_link_add_error)
                }
            }
        }
    }

    override fun onClick(item: LinkItem) {
        val options = arrayOf(
            getString(R.string.send_to_debrid_link),
            getString(R.string.send_to_real_debrid),
            getString(R.string.copy_link),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.type)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sendToDebridLink(item.link)
                    1 -> activityViewModel.downloadSupportedLink(item.link)
                    2 -> {
                        copyToClipboard(getString(R.string.link), item.link)
                        context?.showToast(R.string.link_copied)
                    }
                }
            }
            .show()
    }

    override fun onLongClick(item: LinkItem): Boolean {
        copyToClipboard(getString(R.string.link), item.link)
        context?.showToast(R.string.link_copied)
        return true
    }
}
