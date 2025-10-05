package org.chucc.vcserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetRefTest {

  @Test
  void shouldCreateDatasetRefWithValidValues() {
    // When
    DatasetRef ref = new DatasetRef("my-dataset", "main");

    // Then
    assertThat(ref.datasetName()).isEqualTo("my-dataset");
    assertThat(ref.ref()).isEqualTo("main");
  }

  @Test
  void shouldCreateDatasetRefForBranch() {
    // When
    DatasetRef ref = DatasetRef.forBranch("my-dataset", "develop");

    // Then
    assertThat(ref.datasetName()).isEqualTo("my-dataset");
    assertThat(ref.ref()).isEqualTo("develop");
  }

  @Test
  void shouldCreateDatasetRefForCommit() {
    // Given
    CommitId commitId = CommitId.generate();

    // When
    DatasetRef ref = DatasetRef.forCommit("my-dataset", commitId);

    // Then
    assertThat(ref.datasetName()).isEqualTo("my-dataset");
    assertThat(ref.ref()).isEqualTo(commitId.value());
  }

  @Test
  void shouldThrowExceptionWhenDatasetNameIsNull() {
    assertThatThrownBy(() -> new DatasetRef(null, "main"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Dataset name cannot be null");
  }

  @Test
  void shouldThrowExceptionWhenDatasetNameIsBlank() {
    assertThatThrownBy(() -> new DatasetRef("", "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dataset name cannot be blank");

    assertThatThrownBy(() -> new DatasetRef("   ", "main"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dataset name cannot be blank");
  }

  @Test
  void shouldThrowExceptionWhenRefIsNull() {
    assertThatThrownBy(() -> new DatasetRef("my-dataset", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Ref cannot be null");
  }

  @Test
  void shouldThrowExceptionWhenRefIsBlank() {
    assertThatThrownBy(() -> new DatasetRef("my-dataset", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ref cannot be blank");

    assertThatThrownBy(() -> new DatasetRef("my-dataset", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ref cannot be blank");
  }

  @Test
  void shouldHaveProperToString() {
    // Given
    DatasetRef ref = new DatasetRef("my-dataset", "main");

    // When
    String str = ref.toString();

    // Then
    assertThat(str).isEqualTo("my-dataset@main");
  }

  @Test
  void shouldSupportEquality() {
    // Given
    DatasetRef ref1 = new DatasetRef("my-dataset", "main");
    DatasetRef ref2 = new DatasetRef("my-dataset", "main");
    DatasetRef ref3 = new DatasetRef("my-dataset", "develop");
    DatasetRef ref4 = new DatasetRef("other-dataset", "main");

    // Then
    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1).isNotEqualTo(ref3);
    assertThat(ref1).isNotEqualTo(ref4);
    assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
  }
}
