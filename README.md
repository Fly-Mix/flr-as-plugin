# Flr Plugin

![java](https://img.shields.io/badge/language-java-orange.svg) [![jetbrains plugin version](https://img.shields.io/jetbrains/plugin/v/13789-flr) ![jetbrains plugin downloads](https://img.shields.io/jetbrains/plugin/d/13789-flr)](https://plugins.jetbrains.com/plugin/13789-flr)


Flr (Flutter-R) Plugin: A Flutter Resource Manager Android Studio Plugin, which can help flutter developer to auto specify assets in `pubspec.yaml` and generate `r.g.dart` file after he changes the flutter project assets. With `r.g.dart`,  flutter developer can apply the asset in code by referencing it's asset ID function.

![Flr Usage Example](README_Assets/flr-usage-example.gif)


📖 *Read this in other languages: [English](README.md),  [简体中文](README.zh-cn.md)*

## Feature

- Support auto service that automatically specify assets in `pubspec.yaml` and generate  `r.g.dart` file,  which can be triggered manually or by monitoring asset changes
- Support `R.x` (such as`R.image.test()`, `R.svg.test(width: 100, height: 100)`, `R.txt.test_json()`) code struct 
- Support for processing image assets ( `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.icon`, `.bmp`, `.wbmp`, `.svg` ) 
- Support for processing text assets ( `.txt`, `.json`, `.yaml`, `.xml` ) 
- Support for processing [image asset variants](https://flutter.dev/docs/development/ui/assets-and-images#asset-variants)
- Support for processing asset which’s filename is bad:
   - filename has illegal character (such as  `blank`,  `~`, `@`, `#` ) which is outside the range of  valid characters (`0-9`, `A-Z`, `a-z`, `_`, `+`, `-`, `.`, `·`,  `!`,  `@`,  `&`, `$`, `￥`)
   - filename begins with a number or character `_`  or character`$`
   

## Install Flr plugin

Use the IDE's plugin manager to install the latest version of the plugin:

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Flr"</kbd> > <kbd>Install Plugin</kbd>

![Install Flr](README_Assets/flr-install.png)

## Usage

1. Init your flutter project: Click <kbd>Tools</kbd> > <kbd>Flr</kbd> > <kbd>Init</kbd>

    > The `Flr Init` action will check to see if the current project is a legal flutter project, add flr configuration and dependency [r_dart_library](https://github.com/YK-Unit/r_dart_library) into `pubspec.yaml`.
    >
    > **Attention:**
    >
    > The Flutter SDK is currently in an unstable state, so if you get a build error of `r_dart_library` , you can fix it by modify the dependent version of `r_dart_library`.
    >
    > You can select the correct version of `r_dart_library` based on this [dependency relationship table](https://github.com/YK-Unit/r_dart_library#dependency-relationship-table).
    
2. Open `pubspec.yaml` file, find the configuration item for `Flr`, and then configure the asset directory paths that needs to be scanned by `Flr`, such as:

   ```yaml
    flr:
      version: 0.1.1
      # config the asset directories that need to be scanned
      assets:
      - lib/assets/images
      - lib/assets/texts
   ```

3. Scan assets, specify assets, and generate `r.g.dart`: Click <kbd>Tools</kbd> > <kbd>Flr</kbd> > <kbd>Generate</kbd>

     > After invoke `Flr Generate` action, `Flr` will scan the asset directories configured in `pubspec.yaml`, then specify scanned assets in `pubspec.yaml`, and generate `r.g.dart` file.
     > 
     > **If you want `Flr` to do the above operations automatically every time a asset changes, you can invoke `Flr Start Monitor` action.** (Click <kbd>Tools</kbd> > <kbd>Flr</kbd> > <kbd>Start Monitor</kbd> )
     > Then `Flr` will launche a monitoring service that continuously monitors asset directories configured in `pubspec.yaml`. If the service detects any asset changes, `Flr` will automatically scan the asset directories, then specify scanned assets in pubspec.yaml, and generate "r.g.dart" file. 
     >
     > **You can terminate this monitoring service by invoke `Flr Stop Monitor` action.** (Click <kbd>Tools</kbd> > <kbd>Flr</kbd> > <kbd>Stop Monitor</kbd> )


## r.g.dart

After you invoke `Flr Generate` action or `Flr Start Monitor` action, Flr will scan the asset directories configured in `pubspec.yaml`, then specify scanned assets in `pubspec.yaml`, and generate `r.g.dart` file.

`r.g.dart` defines a asset access interface class: `R`, which allows flutter developer to apply the asset in code by referencing it's asset ID function, such as: 

```dart
import 'package:flutter_r_demo/r.g.dart';

// test_sameName.png
var normalImageWidget = Image(
  width: 200,
  height: 120,
  image: R.image.test_sameName(),
);

// test_sameName.gif
var gifImageWidget = Image(
  image: R.mage.test_sameName_gif(),
);

// test.svg
var svgImageWidget = Image(
  width: 100,
  height: 100,
  image: R.svg.test(width: 100, height: 100),
);

// test.json
var jsonString = await R.text.test_json();

// test.yaml
var yamlString = await R.text.test_yaml();

```

### `_R_X` class

`r.g.dart` defines several private `_R_X` asset management classes: `R_Image`, `R_Svg`, `R_Text`. These private asset management classes are used to manage the asset IDs of the respective asset types:

- `_R_Image`: manage the asset IDs of non-svg type image assets ( `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.icon`, `.bmp`, `.wbmp` )
- `_R_Svg`: manage the asset IDs of svg type image assets
- `_R_Text`: manage the asset IDs of text assets ( `.txt`, `.json`, `.yaml`, `.xml` ) 

### `R` class and `R.x` struct

`r.g.dart` defines a asset access interface class: `R`, which is used to manage common information, aggregate the `_R_X` asset management classes, and implement `R.x` code struct:

```dart
/// This `R` class is generated and contains references to static asset resources.
class R {
  /// package name: flutter_r_demo
  static const package = "flutter_r_demo";

  /// This `R.image` struct is generated, and contains static references to static non-svg type image asset resources.
  static const image = _R_Image();

  /// This `R.svg` struct is generated, and contains static references to static svg type image asset resources.
  static const svg = _R_Svg();

  /// This `R.text` struct is generated, and contains static references to static text asset resources.
  static const text = _R_Text();
}
```

## Example

Here is a [Flutter-R Demo](https://github.com/Fly-Mix/flutter_r_demo) to show how to use Flr tool in flutter project and show how to use `R` class in your code.

## License

The plugin is available as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).
