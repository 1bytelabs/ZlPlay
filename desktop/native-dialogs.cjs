// Keep macOS file panels attached to the foreground application window.
async function showOpenDialog({app, dialog, window, platform=process.platform}, options) {
  if (platform === 'darwin') {
    app.focus({steal: true});
    if (window.isMinimized()) window.restore();
    window.moveTop();
    window.focus();
  }
  return dialog.showOpenDialog(window, options);
}
module.exports = {showOpenDialog};
