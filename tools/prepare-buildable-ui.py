"""Translations for guided buildables and the grouped Zombies scoreboard."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
fr={
 'title':'Objets à construire', 'create_object':'+ Nouvel objet', 'choose_result':'Choisir l’objet obtenu',
 'choose_component':'Choisir la pièce à ramasser', 'piece_name':'Nom de pièce (automatique si vide)',
 'step_result':'1. Nom et objet obtenu', 'step_parts':'2. Pièces à ramasser (%s)',
 'save_step':'Enregistrer et continuer', 'add_component':'+ Ajouter une pièce',
 'create_help':'Donnez un nom, choisissez l’objet final, puis enregistrez. Vous pourrez ensuite ajouter ses pièces ici.',
 'place_help':'Ajoutez une pièce : vous recevez son support prêt à poser. Cliquez sur une pièce ci-dessus pour obtenir un autre emplacement possible.',
 'simple_support_help':'Choisissez la pièce, puis enregistrez. Pour un autre emplacement possible, copiez ce support ou redemandez la même pièce depuis l’établi.',
 'select_recipe_help':'Choisissez un objet à gauche. S’il n’existe pas encore, cliquez sur « Nouvel objet ».',
 'duplicate_piece':'Autre emplacement pour %s', 'nothing_chosen':'Aucun objet choisi',
 'need_result':'Donnez un nom et choisissez l’objet obtenu.', 'search_item':'Rechercher un objet…',
 'inventory':'Mon inventaire', 'all_items':'Tous les objets', 'support_named':'Pièce à poser : %s',
 'place_support':'Support ajouté à votre inventaire : posez-le sur la carte.', 'tab_recipes':'Objets de quête et recettes',
 'advanced':'Options', 'career_compact':'Niveau %s · %s XP'
}
en={
 'title':'Buildable objects', 'create_object':'+ New object', 'choose_result':'Choose the finished object',
 'choose_component':'Choose the component to collect', 'piece_name':'Component name (automatic if empty)',
 'step_result':'1. Name and finished object', 'step_parts':'2. Components to collect (%s)',
 'save_step':'Save and continue', 'add_component':'+ Add a component',
 'create_help':'Enter a name, choose the finished object, then save. You can then add its components here.',
 'place_help':'Add a component to receive a ready-to-place support. Click an existing component above to obtain an alternative spawn location.',
 'simple_support_help':'Choose the component, then save. For an alternative spawn, copy this support or request the same component from a workbench.',
 'select_recipe_help':'Choose an object on the left. If it does not exist yet, click New object.',
 'duplicate_piece':'Alternative location for %s', 'nothing_chosen':'No object selected',
 'need_result':'Enter a name and choose the finished object.', 'search_item':'Search items…',
 'inventory':'My inventory', 'all_items':'All items', 'support_named':'Placeable component: %s',
 'place_support':'Support added to your inventory: place it on the map.', 'tab_recipes':'Quest items and recipes',
 'advanced':'Options', 'career_compact':'Level %s · %s XP'
}
for lang in ['en_us','fr_fr','de_de','es_es','pt_br','ru_ru']:
 path=Path('assets/zombierool/lang')/(lang+'.json')
 n=json.loads((ROOT/'src/main/resources'/path).read_text(encoding='utf8'))
 n.update({'gui.zombierool.buildable.'+k:v for k,v in (fr if lang=='fr_fr' else en).items()})
 for folder in ['src/main/resources','resources','.']:(ROOT/folder/path).write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Guided buildable and scoreboard translations updated.')
