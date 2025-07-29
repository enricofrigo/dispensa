package eu.frigo.dispensa.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CategoryDefinitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CategoryDefinition categoryDefinition);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryDefinition> categoryDefinition);

    @Update
    void update(CategoryDefinition categoryDefinition);

    @Delete
    void delete(CategoryDefinition categoryDefinition);

    @Query("DELETE FROM categories_definitions")
    void deleteAllCategoryDefinitions();
    @Query("SELECT * FROM categories_definitions ")
    LiveData<List<CategoryDefinition>> getAllCategoryDefinitions();

    @Query("SELECT * FROM categories_definitions WHERE category_id = :categoryDefinitionId")
    CategoryDefinition getCategoryDefinitionById(int categoryDefinitionId);

    @Query("SELECT * FROM categories_definitions WHERE tag_name = :apiTag")
    CategoryDefinition getCategoryByTagName(String apiTag);
}
