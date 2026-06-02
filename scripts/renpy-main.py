import os

import runestone_renpy_launcher as launcher


def game_directory(basedir, name):
    root = os.environ["RENPY_GAME_DIR"]
    game = os.path.join(root, "game")
    if os.path.isdir(game):
        return game
    return root


launcher.path_to_gamedir = game_directory
launcher.main()
