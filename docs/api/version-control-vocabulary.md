# CHUCC Version Control Vocabulary

**Namespace:** `http://chucc.org/ns/version-control#`
**Prefix:** `vc:`
**Version:** 1.0
**Status:** Draft

## Overview

This vocabulary extends SPARQL 1.1 Service Description to describe version control capabilities for RDF datasets. It enables discovery of branches, tags, commits, and version control operations.

## Classes

### vc:VersionedDataset

**Type:** `rdfs:Class`
**Subclass of:** `sd:Dataset`
**Definition:** A dataset with version control capabilities (branches, tags, commits)

**Properties:**
- `vc:defaultBranch` - Name of the default branch (e.g., "main")
- `vc:branch` - A branch in this dataset
- `vc:tag` - A tag in this dataset

**Example:**
```turtle
<http://localhost:8080/default> a vc:VersionedDataset ;
  vc:defaultBranch "main" ;
  vc:branch [ ... ] .
```

### vc:Branch

**Type:** `rdfs:Class`
**Definition:** A named mutable reference to a commit

**Properties:**
- `vc:name` - Branch name (string)
- `vc:head` - Current HEAD commit ID
- `vc:protected` - Whether branch is protected from deletion (boolean)
- `vc:createdAt` - Branch creation timestamp
- `vc:updatedAt` - Last update timestamp

**Example:**
```turtle
[
  a vc:Branch ;
  vc:name "main" ;
  vc:head "01936d8f-1234-7890-abcd-ef1234567890" ;
  vc:protected true ;
  vc:createdAt "2025-01-15T10:30:00Z"^^xsd:dateTime
] .
```

### vc:Tag

**Type:** `rdfs:Class`
**Definition:** A named immutable reference to a commit

**Properties:**
- `vc:name` - Tag name (string)
- `vc:commitId` - Target commit ID
- `vc:createdAt` - Tag creation timestamp
- `vc:message` - Optional tag message
- `vc:author` - Tag author

**Example:**
```turtle
[
  a vc:Tag ;
  vc:name "v1.0.0" ;
  vc:commitId "01936d8f-1234-7890-abcd-ef1234567890" ;
  vc:message "Release 1.0.0" ;
  vc:author "John Doe <john@example.org>" ;
  vc:createdAt "2025-01-15T12:00:00Z"^^xsd:dateTime
] .
```

### vc:Commit

**Type:** `rdfs:Class`
**Definition:** A snapshot of the dataset at a point in time

**Properties:**
- `vc:commitId` - Unique commit identifier (UUIDv7)
- `vc:message` - Commit message
- `vc:author` - Commit author
- `vc:timestamp` - Commit timestamp
- `vc:parent` - Parent commit(s)

## Operations

### vc:VersionControl

**Type:** `sd:Feature`
**Definition:** Indicates that the service supports version control operations

### vc:TimeTravel

**Type:** `sd:Feature`
**Definition:** Indicates support for historical queries (asOf selector)

### vc:Merge

**Type:** `sd:Feature`
**Definition:** Indicates support for three-way merge operations

### vc:Rebase

**Type:** `sd:Feature`
**Definition:** Indicates support for rebase operations

### vc:CherryPick

**Type:** `sd:Feature`
**Definition:** Indicates support for cherry-pick operations

### vc:Blame

**Type:** `sd:Feature`
**Definition:** Indicates support for blame (authorship tracking) queries

### vc:Diff

**Type:** `sd:Feature`
**Definition:** Indicates support for computing differences between commits

### vc:Squash

**Type:** `sd:Feature`
**Definition:** Indicates support for squashing multiple commits

### vc:Reset

**Type:** `sd:Feature`
**Definition:** Indicates support for resetting branch pointers

### vc:Revert

**Type:** `sd:Feature`
**Definition:** Indicates support for reverting commits

## Full Example

```turtle
@prefix vc: <http://chucc.org/ns/version-control#> .
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<http://localhost:8080/default> a vc:VersionedDataset ;
  vc:defaultBranch "main" ;
  vc:branch [
    a vc:Branch ;
    vc:name "main" ;
    vc:head "01936d8f-1234-7890-abcd-ef1234567890" ;
    vc:protected true ;
    vc:createdAt "2025-01-15T10:00:00Z"^^xsd:dateTime
  ] ;
  vc:branch [
    a vc:Branch ;
    vc:name "develop" ;
    vc:head "01936d90-5678-7890-abcd-ef1234567890" ;
    vc:protected false ;
    vc:createdAt "2025-01-15T10:30:00Z"^^xsd:dateTime
  ] ;
  vc:tag [
    a vc:Tag ;
    vc:name "v1.0.0" ;
    vc:commitId "01936d8f-1234-7890-abcd-ef1234567890" ;
    vc:message "Initial release" ;
    vc:author "Release Manager <release@example.org>" ;
    vc:createdAt "2025-01-15T12:00:00Z"^^xsd:dateTime
  ] .
```

## Usage in Service Description

The version control vocabulary is designed to be embedded within SPARQL 1.1 Service Descriptions. The service description endpoint (`.well-known/void`) exposes:

1. **Dataset Type**: Each versioned dataset is typed as both `sd:Dataset` and `vc:VersionedDataset`
2. **Branch Information**: All branches are listed with their current HEAD commits
3. **Tag Information**: All tags are listed with their target commits
4. **Feature Declaration**: Service-level features indicate supported operations

This allows SPARQL clients to discover:
- Which datasets support version control
- What branches and tags exist
- Which version control operations are available
- Version control endpoint location

## Related Specifications

- [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
- [VoID (Vocabulary of Interlinked Datasets)](https://www.w3.org/TR/void/)
- [SPARQL 1.2 Protocol Version Control Extension](../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
