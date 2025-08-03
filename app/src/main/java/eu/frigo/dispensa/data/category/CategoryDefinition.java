package eu.frigo.dispensa.data.category;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories_definitions",
        indices = {@Index(value = "tag_name", unique = true)})
public class CategoryDefinition {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    public int categoryId;

    @ColumnInfo(name = "tag_name") // es. "en:dairy"
    public String tagName;

    @ColumnInfo(name = "display_name_it")
    public String displayNameIt; // Opzionale, per UI

    @ColumnInfo(name = "language_code")
    public String languageCode; // Opzionale

    @ColumnInfo(name = "color_hex")
    public String colorHex; // Opzionale

    public CategoryDefinition(String tagName) {
        this.tagName = tagName;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getDisplayNameIt() {
        return displayNameIt;
    }

    public void setDisplayNameIt(String displayNameIt) {
        this.displayNameIt = displayNameIt;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    @Override
    public String toString() {
        return "CategoryDefinition{" +
                "categoryId=" + categoryId +
                ", tagName='" + tagName + '\'' +
                ", displayNameIt='" + displayNameIt + '\'' +
                ", languageCode='" + languageCode + '\'' +
                ", colorHex='" + colorHex + '\'' +
                '}';
    }
}
