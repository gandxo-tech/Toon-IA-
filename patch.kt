            composable(Screen.Personnages.route) {
                CharactersScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNext = { navController.navigate(Screen.Univers.route) }
                )
            }
            composable(Screen.Univers.route) {
                UniverseScreen(
                    projectId = activeProjectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNext = { navController.navigate(Screen.Histoire.route) }
                )
            }
