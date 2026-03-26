__sbe4kt_files=(
  sbe4kt-lib/build.gradle.kts
  sbe4kt-ksp/build.gradle.kts
  sbe4kt-gradle/build.gradle.kts
)

__version_current(){
  sed -n 's/^version = "\(.*\)"/\1/p' "${__sbe4kt_files[1]}"
}

__version_set(){
  local new_version="$1"
  for f in "${__sbe4kt_files[@]}"; do
    sed -i '' "s/^version = \".*\"/version = \"${new_version}\"/" "$f"
  done
  echo "version -> $new_version"
}

__version_increment_patch(){
  local current=$(__version_current)
  local base="${current%-SNAPSHOT}"
  local parts=(${(s/./)base})
  __version_set "${parts[1]}.${parts[2]}.$((parts[3] + 1))-SNAPSHOT"
}

__version_increment_minor(){
  local current=$(__version_current)
  local base="${current%-SNAPSHOT}"
  local parts=(${(s/./)base})
  __version_set "${parts[1]}.$((parts[2] + 1)).0-SNAPSHOT"
}

__version_increment_major(){
  local current=$(__version_current)
  local base="${current%-SNAPSHOT}"
  local parts=(${(s/./)base})
  __version_set "$((parts[1] + 1)).0.0-SNAPSHOT"
}

__version_snapshot(){
  local current=$(__version_current)
  if [[ "$current" != *-SNAPSHOT ]]; then
    __version_set "${current}-SNAPSHOT"
  else
    echo "already SNAPSHOT: $current"
  fi
}

__version_release(){
  local current=$(__version_current)
  if [[ "$current" == *-SNAPSHOT ]]; then
    __version_set "${current%-SNAPSHOT}"
  else
    echo "already release: $current"
  fi
}

__publish_maven_local(){
  ./gradlew :sbe4kt-lib:publishToMavenLocal :sbe4kt-ksp:publishToMavenLocal && \
  ./gradlew -p sbe4kt-gradle publishToMavenLocal && \
  echo "published $(__version_current) to mavenLocal"
}

__check_versions(){
  local gradle_current=$(sed -n 's|.*gradle-\([0-9.]*\)-.*|\1|p' gradle/wrapper/gradle-wrapper.properties)
  local gradle_latest=$(curl -s https://services.gradle.org/versions/current | sed -n 's|.*"version" *: *"\([0-9.]*\)".*|\1|p')
  echo "gradle:  $gradle_current -> ${gradle_latest:-(fetch failed)}"
  echo ""
  ./gradlew dependencyUpdates -q 2>&1 | tail -n +2
}