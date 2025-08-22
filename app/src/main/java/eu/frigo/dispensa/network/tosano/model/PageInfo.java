// PageInfo.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class PageInfo {

    @SerializedName("selPage")
    private int selectedPage;

    @SerializedName("totPages")
    private int totalPages;

    @SerializedName("totItems")
    private int totalItems;

    @SerializedName("itemsPerPage")
    private int itemsPerPage;

    // Getters e Setters
    public int getSelectedPage() {
        return selectedPage;
    }

    public void setSelectedPage(int selectedPage) {
        this.selectedPage = selectedPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    @Override
    public String toString() {
        return "PageInfo{" +
                "selectedPage=" + selectedPage +
                ", totalPages=" + totalPages +
                ", totalItems=" + totalItems +
                ", itemsPerPage=" + itemsPerPage +
                '}';
    }
}