package com.shoppingapp.info.screens.cart

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.shoppingapp.info.R
import com.shoppingapp.info.data.Product
import com.shoppingapp.info.data.User
import com.shoppingapp.info.databinding.CircularLoaderLayoutBinding
import com.shoppingapp.info.databinding.ItemCartBinding


class CartItemAdapter(
    private val context: Context, items: List<User.CartItem>,
    products: List<Product>, userLikes: List<String>
) : RecyclerView.Adapter<CartItemAdapter.ViewHolder>() {

	lateinit var onClickListener: OnClickListener
	var data: List<User.CartItem> = items
	var proList: List<Product> = products
	var likesList: List<String> = userLikes

	inner class ViewHolder(private val binding: ItemCartBinding) :
		RecyclerView.ViewHolder(binding.root) {
		fun bind(item: User.CartItem) {
			binding.loaderLayout.loaderFrameLayout.visibility = View.GONE
			val proData = proList.find { it.productId == item.productId } ?: Product()
			binding.cartProductTitle.text = proData.name
			binding.cartProductPrice.text =
				context.getString(R.string.price_text, proData.price.toString())
			if (proData.images.isNotEmpty()) {
				val imgUrl = proData.images[0].toUri().buildUpon().scheme("https").build()
				Glide.with(context)
					.asBitmap()
					.load(imgUrl)
					.into(binding.productImageView)
				binding.productImageView.clipToOutline = true
			}
			binding.cartProductQuantity.text = item.quantity.toString()




			binding.cartProductDeleteBtn.setOnClickListener {
				binding.loaderLayout.loaderFrameLayout.visibility = View.VISIBLE
				onClickListener.onDeleteClick(item.itemId, binding.loaderLayout)
			}
//			binding.btnCartProductPlus.setOnClickListener {
//				binding.loaderLayout.loaderFrameLayout.visibility = View.VISIBLE
//				onClickListener.onPlusClick(item.itemId)
//			}
//			binding.btnCartProductMinus.setOnClickListener {
//				binding.loaderLayout.loaderFrameLayout.visibility = View.VISIBLE
//				onClickListener.onMinusClick(item.itemId, item.quantity, binding.loaderLayout)
//			}

		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder(
			ItemCartBinding.inflate(
				LayoutInflater.from(parent.context), parent, false
			)
		)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(data[position])
	}

	override fun getItemCount() = data.size

	interface OnClickListener {
		fun onLikeClick(productId: String)
		fun onDeleteClick(itemId: String, itemBinding: CircularLoaderLayoutBinding)
		fun onPlusClick(itemId: String)
		fun onMinusClick(itemId: String, currQuantity: Int, itemBinding: CircularLoaderLayoutBinding)
	}

}